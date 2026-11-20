package io.github.cmdralph.fabricated.core.translate;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.Hashing;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.resolve.PlatformLibraries;

/**
 * Prevents JPMS split packages, which are fatal in NeoForge's single-class-loader module layer.
 *
 * <p>Packages are derived the way NeoForge derives them for a mod's automatic module: every directory
 * containing files whose path is a valid Java package name, excluding {@code assets/}, {@code data/}
 * and {@code META-INF/}. Resource-only directories count, so two mods shipping {@code licenses/LICENSE}
 * would collide. A mod's files are its jar's, with its overlay's copies over them.
 *
 * <ol>
 *   <li>Packages owned by the platform (NeoForge, Minecraft, libraries, Fabricated) are filtered out of
 *       Fabric jars: the platform's copy is used.</li>
 *   <li>When a package already belongs to an earlier Fabric mod (load order), the later mod's copy is
 *       filtered out if every file it has there is byte-identical to the earlier copy.</li>
 *   <li>If the copies differ but the later mod has only resources there, its resources are hidden with a
 *       warning (they are almost never loaded through the class loader).</li>
 *   <li>If the later mod has classes there that the earlier one doesn't (a package split between jars, as
 *       Kotlin and Compose libraries and MixinSquared ship theirs, which Fabric's single class path allows),
 *       the earlier mod's module serves them too: the package stays one module's.</li>
 *   <li>If both have a class of the same name with different bytes (two versions of a library they bundle), the
 *       earlier mod's copy is used, as the first jar on Fabric's class path would be, with a warning.</li>
 * </ol>
 *
 * <p>A module may only declare service providers it has, where Fabric's class path finds a provider in any jar. Once
 * every mod's packages are known, a service file stays where its module has its providers, goes to the one other module
 * that has them (with the packages its mod gave up, or the classes its nested jar holds) where nothing else claims that
 * file's place there, and is left out otherwise, with a note; it never disables its mod ({@link #placeServices}).
 *
 * <p>A nested jar without classes (native libraries, as Skiko's runtime jars hold) lends its root files to the modules
 * of its parent and of the jars nested beside it, whose classes look them up.
 */
public final class PackageGuard {
	/**
	 * Decision for one mod.
	 *
	 * @param excludedPackages packages filtered out of its jar and overlay
	 * @param hiddenFiles      other files filtered out of them: its service files another module declares or none does,
	 *                         and its module descriptors
	 * @param borrowed         files of other mods its module serves too, by the jar they are read from: their part of a
	 *                         package split with this mod, other mods' service files whose providers it has, and the root
	 *                         files of a jar without classes nested beside it
	 * @param provides         the services its module declares, each with its providers, as ServiceLoader reads the files
	 *                         it keeps or serves (lines not naming a class left out): what Forge's module metadata declares
	 *                         ({@link PlatformDescriptor.ServiceFiles#FROM_THE_GUARD})
	 */
	public record Decision(Set<String> excludedPackages, Set<String> hiddenFiles, Map<Path, Set<String>> borrowed, List<String> notes,
			Map<String, List<String>> provides) {
		public static final Decision NONE = new Decision(Set.of(), Set.of(), Map.of(), List.of(), Map.of());

		/** Whether a file (a path relative to the jar's root) is left out of this mod's module. */
		public boolean hides(String relativePath) {
			return hiddenFiles.contains(relativePath) || isExcluded(relativePath, excludedPackages);
		}
	}

	/** A mod that cannot load because of a conflicting package. */
	public record Conflict(TranslatedMod mod, String reason, String suggestion) {
	}

	/** Result: per NeoForge id, the packages to filter; and conflicting mods. */
	public record Result(Map<String, Decision> decisions, List<Conflict> conflicts) {
	}

	/**
	 * A service file as ServiceLoader reads it ({@link #serviceLines}): the class names it lists, in its order, and as a
	 * set; its lines that name no class; the jar it is read from; and whether every platform reads it as Fabric's class
	 * path does ({@link #readable}).
	 */
	private record Service(List<String> names, Set<String> providers, List<String> invalid, Path jar, boolean readable) {
	}

	/**
	 * Where a service file's providers are: by module, each with those it has; those no Fabric mod's module has; and the
	 * jar each held one's class is served from.
	 */
	private record Holders(Map<String, List<String>> modules, List<String> nowhere, Map<String, Path> jars) {
		/** The one module that has them all, or null. */
		String only() {
			return nowhere.isEmpty() && modules.size() == 1 ? modules.keySet().iterator().next() : null;
		}
	}

	/** The service file {@code file} of {@code mod}, which would go to another module. */
	private record Offer(TranslatedMod mod, String file, Service service) {
	}

	/**
	 * What a mod has.
	 *
	 * @param packages   package → its files, each with the jar it is read from (the overlay, or the mod's jar)
	 * @param services   its service files ({@code META-INF/services/...}), by path
	 * @param rootFiles   the files at its root other than mod metadata, with the jar they are read from
	 * @param hasClasses  whether it has any class
	 * @param descriptors its module descriptors ({@code module-info.class}, at the root or a multi-release jar's)
	 */
	private record Contents(Map<String, Map<String, Path>> packages, Map<String, Service> services, Map<String, Path> rootFiles, boolean hasClasses,
			Set<String> descriptors) {
	}

	private static final String SERVICES = "META-INF/services/";
	private static final String MODULE_DESCRIPTOR = "module-info.class";
	/** Root files that describe a jar rather than belong to its code. */
	private static final Set<String> METADATA = Set.of("fabric.mod.json", "quilt.mod.json", "pack.mcmeta", "pack.png");
	private static final String LEFT_OUT = "; the service file is left out, as a module can only declare its own providers";
	private static final String UNREADABLE = ", but the platforms read a service file more strictly than Fabric's class path (UTF-8, and a"
			+ " class name, a blank or a whole-line comment on each line), and the module declaring it would not be built; the service file is left out";
	private static final String AS_THEY_ARE = "the platform reads as it is and would refuse (a class name with a comment after it, a name that isn't"
			+ " a class's, or bytes that aren't UTF-8); the service file is left out, so that its module is built, without the service";
	private static final String SUGGESTION = "Java modules cannot share packages. Remove one of the two mods, or ask the authors to relocate (shade) the shared library.";

	private final ArchiveLimits limits;
	/** Who reads a mod's service files ({@link PlatformDescriptor#serviceFiles}). */
	private final PlatformDescriptor.ServiceFiles serviceFiles;
	/** The platform loader's own package prefixes ({@link PlatformDescriptor#providedPackages}). */
	private final List<String> providedPackages;
	/** The platform libraries' package prefixes the platform's game lacks ({@link PlatformDescriptor#absentLibraryPackages}). */
	private final List<String> absentLibraryPackages;

	public PackageGuard(ArchiveLimits limits, List<String> providedPackages) {
		this(limits, PlatformDescriptor.ServiceFiles.AS_SERVICE_LOADER_DOES, providedPackages, List.of());
	}

	public PackageGuard(ArchiveLimits limits, PlatformDescriptor.ServiceFiles serviceFiles, List<String> providedPackages, List<String> absentLibraryPackages) {
		this.limits = limits;
		this.serviceFiles = serviceFiles;
		this.providedPackages = List.copyOf(providedPackages);
		this.absentLibraryPackages = List.copyOf(absentLibraryPackages);
	}

	/** Whether a package (internal form) is a platform library's that the platform's game lacks ({@link #absentLibraryPackages}). */
	private boolean absentLibrary(String name) {
		String path = name + "/";
		for (String prefix : absentLibraryPackages) {
			if (path.startsWith(prefix)) return true;
		}
		return false;
	}

	/** Whether a package (internal form) is among the platform loader's own ({@link #providedPackages}). */
	private boolean ownedByLoader(String name) {
		String path = name + "/";
		for (String prefix : providedPackages) {
			if (path.startsWith(prefix)) return true;
		}
		return false;
	}

	/**
	 * @param mods             translated mods in load order
	 * @param platformPackages packages (internal form, e.g. {@code net/minecraft/world}) owned by the platform
	 * @param platformClasses  the classes the platform's loaders have outside the game, which decide whether a package of
	 *                         a library the platform ships some modules of is its ({@link PlatformLibraries}, PLAT-2)
	 */
	public Result check(List<TranslatedMod> mods, Set<String> platformPackages, ClassBytesSource platformClasses) {
		List<Conflict> conflicts = new ArrayList<>();
		// Package → the NeoForge id of the mod whose module has it, and every file that module serves there.
		Map<String, String> owner = new HashMap<>();
		Map<String, Map<String, Path>> served = new HashMap<>();
		Map<String, Set<String>> excludedById = new LinkedHashMap<>();
		Map<String, Set<String>> hiddenById = new HashMap<>();
		Map<String, List<String>> notesById = new HashMap<>();
		Map<String, Map<Path, Set<String>>> borrowedById = new HashMap<>();
		// The mods that load, by Fabric id, with what they have.
		Map<String, TranslatedMod> loading = new LinkedHashMap<>();
		Map<String, Contents> contentsById = new HashMap<>();

		for (TranslatedMod mod : mods) {
			Contents contents;
			try {
				contents = contentsOf(mod);
			} catch (IOException e) {
				conflicts.add(new Conflict(mod, "could not inspect its contents: " + e.getMessage(), "Re-download the mod."));
				continue;
			}
			Set<String> excluded = new TreeSet<>();
			List<String> notes = new ArrayList<>();
			// Package → this mod's files there that the package's module will serve.
			Map<String, Map<String, Path>> lent = new TreeMap<>();
			String conflict = null;
			for (Map.Entry<String, Map<String, Path>> pkg : contents.packages().entrySet()) {
				String name = pkg.getKey();
				String dotted = name.replace('/', '.');
				if (platformPackages.contains(name) || !absentLibrary(name) && PlatformLibraries.isProvided(name, pkg.getValue().keySet(), platformClasses)
						|| ownedByLoader(name)) {
					excluded.add(name);
					notes.add("bundles package " + dotted + ", which the platform already provides; using the platform's copy");
					continue;
				}
				String existing = owner.get(name);
				if (existing == null) continue;
				Map<String, Path> ours = pkg.getValue();
				Map<String, Path> extra = new TreeMap<>(ours);
				extra.keySet().removeAll(served.get(name).keySet());
				Set<String> differing;
				try {
					differing = differing(served.get(name), ours);
				} catch (IOException e) {
					conflict = "could not compare package " + dotted + " with " + existing + "'s: " + e.getMessage();
					break;
				}
				boolean hasClasses = ours.keySet().stream().anyMatch(f -> f.endsWith(".class"));
				if (extra.isEmpty() && differing.isEmpty()) {
					notes.add("shares identical files in package " + dotted + " with " + existing + "; using " + existing + "'s copy");
				} else if (!hasClasses) {
					notes.add("has resources in " + dotted + " that clash with " + existing + "; this mod's copies are hidden");
				} else {
					if (!extra.isEmpty()) lent.put(name, extra);
					notes.add("shares package " + dotted + " with " + existing + (extra.isEmpty() ? "" : ", whose module also serves this mod's " + extra.size() + " file(s) there"));
					differing.stream().filter(f -> f.endsWith(".class")).findFirst().ifPresent(clash -> notes.add("WARNING: has a different "
							+ clash.substring(0, clash.length() - ".class".length()).replace('/', '.') + (differing.size() > 1 ? " (and " + (differing.size() - 1) + " more file(s))" : "")
							+ " than " + existing + "; " + existing + "'s copies are used, as the first jar on Fabric's class path would be. Another version of a library both"
							+ " bundle, most likely: if this mod fails, ask its authors to relocate (shade) it."));
				}
				excluded.add(name);
			}
			if (conflict != null) {
				conflicts.add(new Conflict(mod, conflict, SUGGESTION));
				continue;
			}
			// Its service files are placed once every mod's packages are known (placeServices).
			Set<String> hidden = new TreeSet<>();
			// Fabric's class path ignores a module descriptor; the platform would make the jar that module, whose
			// requirements must all be there or no mod loads (SEC-10).
			if (!contents.descriptors().isEmpty()) {
				hidden.addAll(contents.descriptors());
				notes.add("has a module descriptor (module-info.class), which Fabric's class path ignores; left out, so its module is made as other"
						+ " Fabric jars' are");
			}
			lent.forEach((pkg, files) -> {
				String lender = owner.get(pkg);
				files.forEach((file, jar) -> borrowedById.computeIfAbsent(lender, k -> new LinkedHashMap<>()).computeIfAbsent(jar, k -> new TreeSet<>()).add(file));
				served.get(pkg).putAll(files);
				notesById.get(lender).add("also serves the " + files.size() + " file(s) " + mod.neoForgeId() + " has in package " + pkg.replace('/', '.')
						+ " (a package split between them)");
			});
			for (Map.Entry<String, Map<String, Path>> pkg : contents.packages().entrySet()) {
				if (excluded.contains(pkg.getKey())) continue;
				owner.putIfAbsent(pkg.getKey(), mod.neoForgeId());
				served.putIfAbsent(pkg.getKey(), new HashMap<>(pkg.getValue()));
			}
			excludedById.put(mod.neoForgeId(), excluded);
			hiddenById.put(mod.neoForgeId(), hidden);
			notesById.put(mod.neoForgeId(), notes);
			loading.put(mod.candidate().id(), mod);
			contentsById.put(mod.candidate().id(), contents);
		}
		Map<String, Map<String, List<String>>> providesById = new HashMap<>();
		placeServices(loading, contentsById, owner, served, hiddenById, borrowedById, notesById, providesById);
		shareRootFiles(loading, contentsById, borrowedById, notesById);
		Map<String, Decision> decisions = new LinkedHashMap<>();
		excludedById.forEach((id, excluded) -> {
			Map<Path, Set<String>> borrowed = new LinkedHashMap<>();
			borrowedById.getOrDefault(id, Map.of()).forEach((jar, files) -> borrowed.put(jar, Set.copyOf(files)));
			decisions.put(id, new Decision(Set.copyOf(excluded), Set.copyOf(hiddenById.get(id)), Collections.unmodifiableMap(borrowed),
					List.copyOf(notesById.get(id)), Map.copyOf(providesById.getOrDefault(id, Map.of()))));
		});
		return new Result(decisions, conflicts);
	}

	/**
	 * Places every loading mod's service files, once every package's module and the files it serves are known: a mod's
	 * nested jars may come before or after it. A module declares only providers it has, or no mod loads, where Fabric's
	 * class path finds a provider in any jar, or fails only when the service is loaded. A provider is had by the module
	 * serving its package, where that module serves the class: its jar's, or another jar's part of a package split with
	 * it (PKG-1). A service file
	 * <ul>
	 *   <li>stays where its module has every provider: from its own jar, as ever, unless the platform reads it as it is
	 *       and would refuse it ({@link PlatformDescriptor.ServiceFiles#AS_THEY_ARE}); from another jar's part of a
	 *       package split with it (OneConfig 1.8.0's bridges), where every platform reads the file as Fabric does
	 *       ({@link #readable});</li>
	 *   <li>goes to the one other module that has every provider, as a file whose providers went with packages given up
	 *       (PKG-2) or a jar's file naming classes its nested jar holds: where that module is a jar of the mod's own
	 *       (the nest tree of each top-level jar it is in, {@link #families}) and those jars have the classes, so a mod's
	 *       file is declared only by its own jars' modules and only for its own classes; where every platform reads it
	 *       as Fabric does; and where that module doesn't keep a file of the path naming providers itself (one naming
	 *       none makes way, as the platforms skip it). Where other jars' files of the service would go there too, they go
	 *       as one only if they name the same providers (the file of the least mod id is the one served); otherwise none
	 *       goes, as which one Fabric's class path finds first is a matter of order;</li>
	 *   <li>is left out otherwise, the note saying why: a provider no Fabric mod's module has (none does, or the platform's
	 *       or a NeoForge mod's: nothing is put in a module Fabricated doesn't build), providers in several modules, the
	 *       mod's own among them or not (each module could declare only its own, which takes a file no jar has, and
	 *       ServiceLoader would find them module by module, not in the file's order), or one of the conditions above.</li>
	 * </ul>
	 * A service file never disables its mod: leaving it out takes a {@code provides} out of a module, which can't make the
	 * module invalid, where declaring a provider the module lacks stops the game, and the mod's other features stay. The
	 * placement depends on the mods' order only as PKG-1 does: which module serves a package split between jars, the
	 * first jar's, which the note of a file it leaves out says.
	 */
	private void placeServices(Map<String, TranslatedMod> loading, Map<String, Contents> contentsById, Map<String, String> owner,
			Map<String, Map<String, Path>> served, Map<String, Set<String>> hiddenById, Map<String, Map<Path, Set<String>>> borrowedById,
			Map<String, List<String>> notesById, Map<String, Map<String, List<String>>> providesById) {
		Map<String, String> fabricIds = new HashMap<>();
		loading.values().forEach(mod -> fabricIds.put(mod.neoForgeId(), mod.candidate().id()));
		Map<String, Set<String>> families = families(loading);
		// NeoForge id → service file → providers: the files naming providers each module keeps, its own, placed before any
		// other's; and those naming none, which make way for another jar's file of their path.
		Map<String, Map<String, Set<String>>> declared = new HashMap<>();
		Map<String, Set<String>> empty = new HashMap<>();
		// Module → service file → the other mods' files whose providers all that module has.
		Map<String, Map<String, List<Offer>>> offers = new TreeMap<>();
		for (TranslatedMod mod : loading.values()) {
			String id = mod.neoForgeId();
			Contents contents = contentsById.get(mod.candidate().id());
			for (Map.Entry<String, Service> e : contents.services().entrySet()) {
				String file = e.getKey();
				Service service = e.getValue();
				Holders where = holders(service, owner, served);
				String module = where.only();
				boolean ownJar = where.jars().values().stream().allMatch(jar -> jar.equals(mod.jar()) || jar.equals(mod.overlay()));
				boolean asItIs = service.providers().isEmpty() && service.invalid().isEmpty() || id.equals(module) && ownJar;
				List<String> notes = notesById.get(id);
				if (asItIs && serviceFiles == PlatformDescriptor.ServiceFiles.AS_THEY_ARE && !service.readable()) {
					hiddenById.get(id).add(file);
					notes.add("has the service file " + file + ", which " + AS_THEY_ARE);
				} else if (!service.invalid().isEmpty() && serviceFiles != PlatformDescriptor.ServiceFiles.FROM_THE_GUARD) {
					// The platform reads the file itself, and a line naming no class names no provider its module has.
					hiddenById.get(id).add(file);
					notes.add("declares the service " + file.substring(SERVICES.length()) + " with the line " + service.invalid().get(0)
							+ ", which isn't a class name" + LEFT_OUT);
				} else if (asItIs || id.equals(module) && service.readable()) {
					keep(id, file, service, declared, empty, notes, providesById);
				} else {
					hiddenById.get(id).add(file);
					if (id.equals(module)) {
						notes.add(declares(file, service.providers()) + ", which its module serves from another jar's part of a package split with it" + UNREADABLE);
					} else if (!where.nowhere().isEmpty()) {
						notes.add(declares(file, where.nowhere()) + ", which no Fabric mod's module has" + LEFT_OUT);
					} else if (module == null) {
						notes.add("declares the service " + file.substring(SERVICES.length()) + " with providers in the modules of "
								+ String.join(" and ", where.modules().keySet()) + LEFT_OUT + splitDecided(id, contents, where));
					} else {
						offers.computeIfAbsent(module, k -> new TreeMap<>()).computeIfAbsent(file, k -> new ArrayList<>()).add(new Offer(mod, file, service));
					}
				}
			}
		}
		offers.forEach((module, byFile) -> byFile.forEach((file, all) -> {
			Set<String> theirs = declared.getOrDefault(module, Map.of()).get(file);
			List<Offer> going = new ArrayList<>();
			for (Offer offer : all) {
				Set<String> providers = offer.service().providers();
				Set<String> family = families.get(offer.mod().candidate().id());
				List<String> notes = notesById.get(offer.mod().neoForgeId());
				String has = declares(file, providers) + ", which " + module + "'s module has";
				if (theirs != null) {
					notes.add(has + (theirs.equals(providers) ? " and declares already; the service file is left out, as that module serves it"
							: " but declares with other providers in its own file" + LEFT_OUT));
				} else if (!family.contains(fabricIds.get(module))) {
					notes.add(has + ": the package " + packageOf(providers.iterator().next()).replace('/', '.') + " is served by another mod; the"
							+ " service file is left out, as a mod's file is declared only by its own jars' modules");
				} else if (!providers.stream().allMatch(p -> familyHas(family, classFile(p), contentsById))) {
					notes.add(has + " from another mod's jar, none of this mod's own (the jars nested with it) having the class; the service file is"
							+ " left out, as a mod's file is declared only for its own classes");
				} else if (!offer.service().readable()) {
					notes.add(has + UNREADABLE);
				} else {
					going.add(offer);
				}
			}
			if (going.isEmpty()) return;
			// Files naming the same providers serve the service once, from the least mod id's jar: whichever order the mods
			// come in. Files naming other providers can't share the path in one module, and which one Fabric's class path
			// finds first is a matter of order, so none goes.
			Set<String> providers = going.get(0).service().providers();
			if (going.stream().allMatch(o -> o.service().providers().equals(providers))) {
				Offer chosen = going.stream().min(Comparator.comparing(o -> o.mod().neoForgeId())).orElseThrow();
				if (empty.getOrDefault(module, Set.of()).contains(file)) {
					hiddenById.get(module).add(file);
					notesById.get(module).add("has the service file " + file + ", which names no provider and makes way for " + chosen.mod().neoForgeId()
							+ "'s, as the platforms skip a file naming none");
				}
				declareFor(module, chosen.mod().neoForgeId(), file, chosen.service(), borrowedById, notesById, providesById);
				for (Offer offer : going) {
					notesById.get(offer.mod().neoForgeId()).add(declares(file, providers) + ", which " + module + "'s module has"
							+ (offer == chosen ? ": the service file goes there, as a module can only declare its own providers"
							: " and declares already, from " + chosen.mod().neoForgeId() + "'s file of it; the service file is left out, as that module serves it"));
				}
				return;
			}
			for (Offer offer : going) {
				List<String> others = going.stream().filter(o -> o != offer).map(o -> o.mod().neoForgeId()).sorted().toList();
				notesById.get(offer.mod().neoForgeId()).add(declares(file, offer.service().providers()) + ", which " + module + "'s module has, as the"
						+ " files of that service of " + String.join(" and ", others) + " would go there with other providers; each is left out, as"
						+ " a module has one file of a path");
			}
		}));
	}

	/**
	 * The module {@code id} keeps its own service file: a file naming no provider may make way for another jar's, one
	 * naming providers is declared, as ServiceLoader reads it. Where Fabricated writes the module's metadata
	 * ({@link PlatformDescriptor.ServiceFiles#FROM_THE_GUARD}) a file or a line naming no class is left out of what it
	 * declares, which the notes say.
	 */
	private void keep(String id, String file, Service service, Map<String, Map<String, Set<String>>> declared, Map<String, Set<String>> empty,
			List<String> notes, Map<String, Map<String, List<String>>> providesById) {
		boolean guard = serviceFiles == PlatformDescriptor.ServiceFiles.FROM_THE_GUARD;
		if (guard && !validServiceName(file)) {
			notes.add("has the service file " + file + ", whose name isn't a class's directly in " + SERVICES + "; its module doesn't declare it, as Java's"
					+ " modules take only class names");
			return;
		}
		if (guard && !service.invalid().isEmpty()) {
			int more = service.invalid().size() - 1;
			notes.add("declares the service " + file.substring(SERVICES.length()) + " with the line " + service.invalid().get(0) + (more > 0 ? " and " + more
					+ " more line(s), which aren't class names" : ", which isn't a class name") + "; its module declares the service without "
					+ (more > 0 ? "them" : "it") + ", as Java's modules take only class names");
		}
		if (service.providers().isEmpty()) {
			empty.computeIfAbsent(id, k -> new TreeSet<>()).add(file);
			return;
		}
		declared.computeIfAbsent(id, k -> new HashMap<>()).put(file, service.providers());
		if (validServiceName(file)) providesById.computeIfAbsent(id, k -> new TreeMap<>()).put(file.substring(SERVICES.length()), service.names());
	}

	/**
	 * Where the providers of a file left out for being in several modules are in another module because its jar came
	 * first in a package split with this mod's: the note's end saying so, as in the other order this mod's would serve them.
	 */
	private static String splitDecided(String id, Contents contents, Holders where) {
		Set<String> split = new TreeSet<>();
		where.modules().forEach((module, held) -> {
			if (module.equals(id)) return;
			for (String provider : held) {
				if (contents.packages().containsKey(packageOf(provider))) split.add(packageOf(provider).replace('/', '.'));
			}
		});
		return split.isEmpty() ? "" : ". The package split decided it: " + String.join(" and ", split) + ", which this mod's jar has classes in too, is"
				+ " served by the module of the jar that came first";
	}

	/** "declares the service S with" the first of {@code providers}, and how many more there are. */
	private static String declares(String file, Collection<String> providers) {
		return "declares the service " + file.substring(SERVICES.length()) + " with " + some(providers);
	}

	/** The first of {@code names}, and how many more there are. */
	private static String some(Collection<String> names) {
		return names.iterator().next() + (names.size() > 1 ? " and " + (names.size() - 1) + " more provider(s)" : "");
	}

	/** Where a service file's providers are: each in the module serving its package, where that module serves the class. */
	private static Holders holders(Service service, Map<String, String> owner, Map<String, Map<String, Path>> served) {
		Map<String, List<String>> modules = new TreeMap<>();
		List<String> nowhere = new ArrayList<>();
		Map<String, Path> jars = new TreeMap<>();
		for (String provider : service.providers()) {
			String pkg = packageOf(provider);
			Path jar = served.getOrDefault(pkg, Map.of()).get(classFile(provider));
			if (jar != null) {
				modules.computeIfAbsent(owner.get(pkg), k -> new ArrayList<>()).add(provider);
				jars.put(provider, jar);
			} else {
				nowhere.add(provider);
			}
		}
		return new Holders(modules, nowhere, jars);
	}

	/**
	 * Each loading mod's own jars, by Fabric id: the nest tree of every top-level jar it is nested in, at any depth. A
	 * library several mods nest one copy of (the scanner keeps one, each nesting mod listing it) is in each of their
	 * trees, and their trees are its.
	 */
	private static Map<String, Set<String>> families(Map<String, TranslatedMod> loading) {
		Map<String, Set<String>> parents = new HashMap<>();
		Map<String, Set<String>> children = new HashMap<>();
		for (TranslatedMod mod : loading.values()) {
			String id = mod.candidate().id();
			if (mod.candidate().parentId() != null) {
				parents.computeIfAbsent(id, k -> new TreeSet<>()).add(mod.candidate().parentId());
				children.computeIfAbsent(mod.candidate().parentId(), k -> new TreeSet<>()).add(id);
			}
			for (String child : mod.candidate().nestedChildren()) {
				parents.computeIfAbsent(child, k -> new TreeSet<>()).add(id);
				children.computeIfAbsent(id, k -> new TreeSet<>()).add(child);
			}
		}
		Map<String, Set<String>> families = new HashMap<>();
		for (String id : loading.keySet()) {
			Set<String> family = new TreeSet<>();
			for (String up : reach(id, parents)) {
				if (!parents.containsKey(up)) family.addAll(reach(up, children));
			}
			families.put(id, family);
		}
		return families;
	}

	/** {@code from} and every id the edges lead to from it. */
	private static Set<String> reach(String from, Map<String, Set<String>> edges) {
		Set<String> reached = new TreeSet<>();
		List<String> next = new ArrayList<>(List.of(from));
		while (!next.isEmpty()) {
			String id = next.remove(next.size() - 1);
			if (reached.add(id)) next.addAll(edges.getOrDefault(id, Set.of()));
		}
		return reached;
	}

	/** Whether one of the jars of {@code family} has the class. */
	private static boolean familyHas(Set<String> family, String classFile, Map<String, Contents> contentsById) {
		String pkg = classFile.substring(0, Math.max(classFile.lastIndexOf('/'), 0));
		for (String id : family) {
			Contents contents = contentsById.get(id);
			Map<String, Path> files = contents == null ? null : contents.packages().get(pkg);
			if (files != null && files.containsKey(classFile)) return true;
		}
		return false;
	}

	/** {@code module} declares the mod {@code id}'s service file too, served from the jar it is read from. */
	private static void declareFor(String module, String id, String file, Service service, Map<String, Map<Path, Set<String>>> borrowedById,
			Map<String, List<String>> notesById, Map<String, Map<String, List<String>>> providesById) {
		borrowedById.computeIfAbsent(module, k -> new LinkedHashMap<>()).computeIfAbsent(service.jar(), k -> new TreeSet<>()).add(file);
		providesById.computeIfAbsent(module, k -> new TreeMap<>()).put(file.substring(SERVICES.length()), service.names());
		notesById.get(module).add("also declares " + id + "'s service " + file.substring(SERVICES.length()) + ", whose providers it has");
	}

	/**
	 * The root files of a nested jar without classes (native libraries, as Skiko's runtime jars hold) are served by the
	 * modules of its parent and of the jars nested beside it too: the classes there look them up with
	 * {@code Class.getResource}, which in a module searches that module only, where Fabric's class path finds any jar's.
	 * Files of no package may be in several modules; one a module has itself stays its own.
	 */
	private static void shareRootFiles(Map<String, TranslatedMod> loading, Map<String, Contents> contentsById,
			Map<String, Map<Path, Set<String>>> borrowedById, Map<String, List<String>> notesById) {
		for (Map.Entry<String, TranslatedMod> e : loading.entrySet()) {
			Contents contents = contentsById.get(e.getKey());
			String parent = e.getValue().candidate().parentId();
			if (contents.hasClasses() || contents.rootFiles().isEmpty() || parent == null) continue;
			List<String> users = new ArrayList<>();
			for (Map.Entry<String, TranslatedMod> other : loading.entrySet()) {
				Contents theirs = contentsById.get(other.getKey());
				boolean related = other.getKey().equals(parent) || parent.equals(other.getValue().candidate().parentId());
				if (!related || !theirs.hasClasses()) continue;
				String id = other.getValue().neoForgeId();
				Map<Path, Set<String>> borrowed = borrowedById.computeIfAbsent(id, k -> new LinkedHashMap<>());
				contents.rootFiles().forEach((file, jar) -> {
					boolean taken = theirs.rootFiles().containsKey(file) || borrowed.values().stream().anyMatch(files -> files.contains(file));
					if (!taken) borrowed.computeIfAbsent(jar, k -> new TreeSet<>()).add(file);
				});
				users.add(id);
			}
			if (!users.isEmpty()) {
				notesById.get(e.getValue().neoForgeId()).add("has no classes: its " + contents.rootFiles().size() + " root file(s) are also served by the modules of "
						+ String.join(", ", users) + ", whose classes look them up (as on Fabric's class path)");
			}
		}
	}

	/** The mod's files by package (its overlay's copies over its jar's), its service files and its other root files. */
	private Contents contentsOf(TranslatedMod mod) throws IOException {
		Map<String, Map<String, Path>> packages = new TreeMap<>();
		Map<String, Service> services = new TreeMap<>();
		Map<String, Path> rootFiles = new TreeMap<>();
		Set<String> descriptors = new TreeSet<>();
		boolean hasClasses = false;
		for (Path jar : mod.overlay() != null && Files.exists(mod.overlay()) ? List.of(mod.jar(), mod.overlay()) : List.of(mod.jar())) {
			try (SafeArchive archive = SafeArchive.open(jar, limits.forTranslatedJars())) {
				for (String name : archive.entryNames()) {
					if (name.startsWith(SERVICES) && !name.endsWith("/")) {
						services.put(name, service(name, archive.read(name), jar));
						continue;
					}
					if (name.equals(MODULE_DESCRIPTOR) || name.startsWith("META-INF/versions/") && name.endsWith("/" + MODULE_DESCRIPTOR)) {
						descriptors.add(name);
						continue;
					}
					if (name.endsWith("/") || name.startsWith("META-INF/") || name.startsWith("assets/") || name.startsWith("data/")) continue;
					if (name.endsWith(MODULE_DESCRIPTOR)) continue;
					hasClasses |= name.endsWith(".class");
					int slash = name.lastIndexOf('/');
					if (slash < 0 && !METADATA.contains(name)) rootFiles.put(name, jar);
					if (slash < 1) continue; // default package
					String pkg = name.substring(0, slash);
					if (!isPackageName(pkg)) continue;
					packages.computeIfAbsent(pkg, k -> new TreeMap<>()).put(name, jar);
				}
			}
		}
		return new Contents(packages, services, rootFiles, hasClasses, descriptors);
	}

	/** A service file as ServiceLoader reads it ({@link #serviceLines}), and whether every platform reads it so ({@link #readable}). */
	private static Service service(String file, byte[] data, Path jar) {
		List<String> names = new ArrayList<>();
		List<String> invalid = new ArrayList<>();
		for (String line : serviceLines(new String(data, StandardCharsets.UTF_8))) {
			if (!isBinaryName(line)) invalid.add(line);
			else if (!names.contains(line)) names.add(line);
		}
		return new Service(List.copyOf(names), new TreeSet<>(names), List.copyOf(invalid), jar, readable(file, data));
	}

	/**
	 * The lines of a service file as ServiceLoader reads them on Fabric's class path: split as {@code BufferedReader}
	 * splits them, a comment from any {@code #} cut, trimmed, blank ones skipped.
	 */
	static List<String> serviceLines(String text) {
		List<String> lines = new ArrayList<>();
		for (String line : text.split(LINE_END, -1)) {
			int comment = line.indexOf('#');
			String name = (comment >= 0 ? line.substring(0, comment) : line).trim();
			if (!name.isEmpty()) lines.add(name);
		}
		return lines;
	}

	/** The class names a service file lists, as ServiceLoader reads it ({@link #serviceLines}): lines naming no class left out. */
	static Set<String> providers(String serviceFile) {
		Set<String> providers = new TreeSet<>();
		for (String line : serviceLines(serviceFile)) {
			if (isBinaryName(line)) providers.add(line);
		}
		return providers;
	}

	/**
	 * Whether every platform reads a service file as Fabric's class path does, which a file placed by another jar's classes
	 * must be. SecureJarHandler's {@code Provider.fromPath}, with which NeoForge 21.1 (SecureJarHandler 3) and Forge 43 and
	 * 47 (SecureJarHandler 2.1) read a module's service files, takes them as strict UTF-8 and every line, trimmed, that is
	 * neither blank nor begun by {@code #} as a provider's binary name, a comment after a name and all; Forge on
	 * securemodules and NeoForge's FML 10 to 12 cut such a comment, as ServiceLoader does. Java's modules take only a
	 * binary class name, the service's (the file's name, directly in {@code META-INF/services}) and each provider's. So
	 * this takes a file so named, in UTF-8, with a binary name, a blank or a whole-line comment on each line, its lines
	 * split as {@code BufferedReader} splits them (as {@code Files.readAllLines} reads them).
	 */
	static boolean readable(String file, byte[] data) {
		if (!validServiceName(file)) return false;
		String text;
		try {
			text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(data)).toString();
		} catch (CharacterCodingException e) {
			return false;
		}
		for (String line : text.split(LINE_END, -1)) {
			String name = line.trim();
			if (!name.isEmpty() && !name.startsWith("#") && !isBinaryName(name)) return false;
		}
		return true;
	}

	/** Whether a service file's name is one Java's modules take: a binary class name, the file directly in {@code META-INF/services}. */
	static boolean validServiceName(String file) {
		String name = file.substring(SERVICES.length());
		return name.indexOf('/') < 0 && isBinaryName(name);
	}

	/** The line ends {@code BufferedReader.readLine} knows; no other character, U+2028 included, ends a line. */
	private static final String LINE_END = "\r\n|\r|\n";

	/** Java's keywords and literals, which no part of a class's name may be (the JDK's module checks refuse them). */
	private static final Set<String> RESERVED = Set.of("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
			"continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import",
			"instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static",
			"strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
			"null", "_");

	/** Whether {@code name} is a binary class name: Java identifiers joined by dots, no character an identifier ignores. */
	static boolean isBinaryName(String name) {
		for (String part : name.split("\\.", -1)) {
			if (part.isEmpty() || RESERVED.contains(part)) return false;
			for (int i = 0; i < part.length(); ) {
				int c = part.codePointAt(i);
				if (Character.isIdentifierIgnorable(c) || !(i == 0 ? Character.isJavaIdentifierStart(c) : Character.isJavaIdentifierPart(c))) return false;
				i += Character.charCount(c);
			}
		}
		return true;
	}

	/** The class file of a binary class name. */
	private static String classFile(String className) {
		return className.replace('.', '/') + ".class";
	}

	/** The package (internal form) of a binary class name. */
	private static String packageOf(String className) {
		int dot = className.lastIndexOf('.');
		return dot < 0 ? "" : className.substring(0, dot).replace('.', '/');
	}

	/** Mirrors the JLS check NeoForge uses: every segment must be a Java identifier. */
	static boolean isPackageName(String internal) {
		for (String segment : internal.split("/", -1)) {
			if (segment.isEmpty() || !Character.isJavaIdentifierStart(segment.charAt(0))) return false;
			for (int i = 1; i < segment.length(); i++) {
				if (!Character.isJavaIdentifierPart(segment.charAt(i))) return false;
			}
		}
		return true;
	}

	/** The files both have whose bytes differ (only those are hashed). */
	private Set<String> differing(Map<String, Path> theirs, Map<String, Path> ours) throws IOException {
		Set<String> shared = new TreeSet<>(ours.keySet());
		shared.retainAll(theirs.keySet());
		if (shared.isEmpty()) return Set.of();
		Map<String, String> a = hashes(theirs, shared);
		Map<String, String> b = hashes(ours, shared);
		Set<String> differing = new TreeSet<>();
		for (String name : shared) {
			if (!a.get(name).equals(b.get(name))) differing.add(name);
		}
		return differing;
	}

	/** SHA-256 of each of {@code names}, read from the jar {@code files} names for it. */
	private Map<String, String> hashes(Map<String, Path> files, Set<String> names) throws IOException {
		Map<Path, List<String>> byJar = new LinkedHashMap<>();
		for (String name : names) byJar.computeIfAbsent(files.get(name), k -> new ArrayList<>()).add(name);
		Map<String, String> ret = new HashMap<>();
		for (Map.Entry<Path, List<String>> e : byJar.entrySet()) {
			try (SafeArchive archive = SafeArchive.open(e.getKey(), limits.forTranslatedJars())) {
				for (String name : e.getValue()) {
					byte[] data = archive.read(name);
					ret.put(name, data == null ? "" : Hashing.sha256(data));
				}
			}
		}
		return ret;
	}

	/** Whether {@code relativePath} is a file directly inside one of the {@code excluded} packages. */
	public static boolean isExcluded(String relativePath, Set<String> excluded) {
		if (excluded.isEmpty() || relativePath.endsWith("/")) return false;
		int slash = relativePath.lastIndexOf('/');
		return slash > 0 && excluded.contains(relativePath.substring(0, slash));
	}
}
