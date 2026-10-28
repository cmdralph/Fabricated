package io.github.cmdralph.fabricated.core.translate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
 * <p>A module may only declare service providers it has, so a service file of the later mod whose providers are in
 * packages it gave up goes with them: hidden when the module that has them declares the same service with the same
 * providers, served by that module when it doesn't declare it. Any other case (providers in more than one module,
 * or the same service with other providers there) disables the later mod.
 *
 * <p>A nested jar without classes (native libraries, as Skiko's runtime jars hold) lends its root files to the modules
 * of its parent and of the jars nested beside it, whose classes look them up.
 */
public final class PackageGuard {
	/**
	 * Decision for one mod.
	 *
	 * @param excludedPackages packages filtered out of its jar and overlay
	 * @param hiddenFiles      other files filtered out of them: service files whose providers another module has
	 * @param borrowed         files of other mods its module serves too, by the jar they are read from: their part of a
	 *                         package split with this mod, the service files that go with it, and the root files of a
	 *                         jar without classes nested beside it
	 */
	public record Decision(Set<String> excludedPackages, Set<String> hiddenFiles, Map<Path, Set<String>> borrowed, List<String> notes) {
		public static final Decision NONE = new Decision(Set.of(), Set.of(), Map.of(), List.of());

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

	/** A service file: the providers it lists, and the jar it is read from. */
	private record Service(Set<String> providers, Path jar) {
	}

	/**
	 * What a mod has.
	 *
	 * @param packages   package → its files, each with the jar it is read from (the overlay, or the mod's jar)
	 * @param services   its service files ({@code META-INF/services/...}), by path
	 * @param rootFiles  the files at its root other than mod metadata, with the jar they are read from
	 * @param hasClasses whether it has any class
	 */
	private record Contents(Map<String, Map<String, Path>> packages, Map<String, Service> services, Map<String, Path> rootFiles, boolean hasClasses) {
	}

	private static final String SERVICES = "META-INF/services/";
	/** Root files that describe a jar rather than belong to its code. */
	private static final Set<String> METADATA = Set.of("fabric.mod.json", "quilt.mod.json", "pack.mcmeta", "pack.png");
	private static final String SUGGESTION = "Java modules cannot share packages. Remove one of the two mods, or ask the authors to relocate (shade) the shared library.";

	private final ArchiveLimits limits;
	/** The platform loader's own package prefixes ({@link PlatformDescriptor#providedPackages}). */
	private final List<String> providedPackages;

	public PackageGuard(ArchiveLimits limits, List<String> providedPackages) {
		this.limits = limits;
		this.providedPackages = List.copyOf(providedPackages);
	}

	/** Whether a package (internal form) is among the platform loader's own ({@link #providedPackages}). */
	private boolean ownedByLoader(String name) {
		for (String prefix : providedPackages) {
			if ((name + "/").startsWith(prefix)) return true;
		}
		return false;
	}

	/**
	 * @param mods             translated mods in load order
	 * @param platformPackages packages (internal form, e.g. {@code net/minecraft/world}) owned by the platform
	 */
	public Result check(List<TranslatedMod> mods, Set<String> platformPackages) {
		List<Conflict> conflicts = new ArrayList<>();
		// Package → the NeoForge id of the mod whose module has it, and every file that module serves there.
		Map<String, String> owner = new HashMap<>();
		Map<String, Map<String, Path>> served = new HashMap<>();
		// NeoForge id → the services its module declares (service file → providers).
		Map<String, Map<String, Set<String>>> declared = new HashMap<>();
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
			// The packages this mod gives up to another Fabric mod's module.
			Set<String> givenUp = new TreeSet<>();
			List<String> notes = new ArrayList<>();
			// Package → this mod's files there that the package's module will serve.
			Map<String, Map<String, Path>> lent = new TreeMap<>();
			String conflict = null;
			for (Map.Entry<String, Map<String, Path>> pkg : contents.packages().entrySet()) {
				String name = pkg.getKey();
				String dotted = name.replace('/', '.');
				if (platformPackages.contains(name) || PlatformLibraries.isProvided(name + "/") || ownedByLoader(name)) {
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
				givenUp.add(name);
			}
			// Service files whose providers went with the packages given up.
			Set<String> hidden = new TreeSet<>();
			// Module → the service files it will serve for this mod.
			Map<String, Map<String, Service>> moved = new TreeMap<>();
			for (Map.Entry<String, Service> service : conflict == null ? contents.services().entrySet() : Set.<Map.Entry<String, Service>>of()) {
				Set<String> providerPackages = new TreeSet<>();
				for (String provider : service.getValue().providers()) providerPackages.add(packageOf(provider));
				if (Collections.disjoint(providerPackages, givenUp)) continue;
				Set<String> modules = new TreeSet<>();
				for (String pkg : providerPackages) modules.add(givenUp.contains(pkg) ? owner.get(pkg) : mod.neoForgeId());
				String file = service.getKey();
				if (modules.size() > 1) {
					conflict = "declares the service " + file.substring(SERVICES.length()) + " with providers in packages of " + String.join(" and ", modules)
							+ ", and a module can only declare its own";
					break;
				}
				String module = modules.iterator().next();
				Set<String> theirs = declared.getOrDefault(module, Map.of()).get(file);
				if (theirs == null) {
					moved.computeIfAbsent(module, k -> new TreeMap<>()).put(file, service.getValue());
				} else if (!theirs.equals(service.getValue().providers())) {
					conflict = "declares the service " + file.substring(SERVICES.length()) + " with other providers than " + module
							+ ", whose module has them";
					break;
				}
				hidden.add(file);
			}
			if (conflict != null) {
				conflicts.add(new Conflict(mod, conflict, SUGGESTION));
				continue;
			}
			// A module declares only providers it has, or the game doesn't start. Fabric's class path finds a provider in
			// any jar, or fails when the service is loaded: a shaded library's service file naming a class the jar
			// dropped, or one the platform's copy of the package has, costs nothing there.
			for (Map.Entry<String, Service> service : contents.services().entrySet()) {
				if (hidden.contains(service.getKey())) continue;
				List<String> missing = service.getValue().providers().stream().filter(p -> !serves(contents, excluded, p)).toList();
				if (missing.isEmpty()) continue;
				hidden.add(service.getKey());
				notes.add("declares the service " + service.getKey().substring(SERVICES.length()) + " with " + missing.get(0)
						+ (missing.size() > 1 ? " and " + (missing.size() - 1) + " more provider(s)" : "")
						+ ", which its module doesn't have; the service file is left out, as a module can only declare its own providers");
			}
			lent.forEach((pkg, files) -> {
				String lender = owner.get(pkg);
				files.forEach((file, jar) -> borrowedById.computeIfAbsent(lender, k -> new LinkedHashMap<>()).computeIfAbsent(jar, k -> new TreeSet<>()).add(file));
				served.get(pkg).putAll(files);
				notesById.get(lender).add("also serves the " + files.size() + " file(s) " + mod.neoForgeId() + " has in package " + pkg.replace('/', '.')
						+ " (a package split between them)");
			});
			moved.forEach((module, files) -> files.forEach((file, service) -> {
				borrowedById.computeIfAbsent(module, k -> new LinkedHashMap<>()).computeIfAbsent(service.jar(), k -> new TreeSet<>()).add(file);
				declared.computeIfAbsent(module, k -> new HashMap<>()).put(file, service.providers());
				notesById.get(module).add("also declares " + mod.neoForgeId() + "'s service " + file.substring(SERVICES.length()) + ", whose providers it has");
			}));
			for (Map.Entry<String, Map<String, Path>> pkg : contents.packages().entrySet()) {
				if (excluded.contains(pkg.getKey())) continue;
				owner.putIfAbsent(pkg.getKey(), mod.neoForgeId());
				served.putIfAbsent(pkg.getKey(), new HashMap<>(pkg.getValue()));
			}
			contents.services().forEach((file, service) -> {
				if (!hidden.contains(file)) declared.computeIfAbsent(mod.neoForgeId(), k -> new HashMap<>()).put(file, service.providers());
			});
			excludedById.put(mod.neoForgeId(), excluded);
			hiddenById.put(mod.neoForgeId(), hidden);
			notesById.put(mod.neoForgeId(), notes);
			loading.put(mod.candidate().id(), mod);
			contentsById.put(mod.candidate().id(), contents);
		}
		shareRootFiles(loading, contentsById, borrowedById, notesById);
		Map<String, Decision> decisions = new LinkedHashMap<>();
		excludedById.forEach((id, excluded) -> {
			Map<Path, Set<String>> borrowed = new LinkedHashMap<>();
			borrowedById.getOrDefault(id, Map.of()).forEach((jar, files) -> borrowed.put(jar, Set.copyOf(files)));
			decisions.put(id, new Decision(Set.copyOf(excluded), Set.copyOf(hiddenById.get(id)), Collections.unmodifiableMap(borrowed),
					List.copyOf(notesById.get(id))));
		});
		return new Result(decisions, conflicts);
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
		boolean hasClasses = false;
		for (Path jar : mod.overlay() != null && Files.exists(mod.overlay()) ? List.of(mod.jar(), mod.overlay()) : List.of(mod.jar())) {
			try (SafeArchive archive = SafeArchive.open(jar, limits.forTranslatedJars())) {
				for (String name : archive.entryNames()) {
					if (name.startsWith(SERVICES) && !name.endsWith("/")) {
						services.put(name, new Service(providers(archive.readText(name)), jar));
						continue;
					}
					if (name.endsWith("/") || name.startsWith("META-INF/") || name.startsWith("assets/") || name.startsWith("data/")) continue;
					if (name.endsWith("module-info.class")) continue;
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
		return new Contents(packages, services, rootFiles, hasClasses);
	}

	/** The providers a service file lists (binary class names), as NeoForge reads it: comments and blank lines ignored. */
	static Set<String> providers(String serviceFile) {
		Set<String> providers = new TreeSet<>();
		for (String line : serviceFile.split("\\R")) {
			int comment = line.indexOf('#');
			String provider = (comment >= 0 ? line.substring(0, comment) : line).trim();
			if (!provider.isEmpty()) providers.add(provider);
		}
		return providers;
	}

	/** Whether the mod's module has the class {@code className} (binary name): in one of its packages it keeps. */
	private static boolean serves(Contents contents, Set<String> excluded, String className) {
		String pkg = packageOf(className);
		Map<String, Path> files = contents.packages().get(pkg);
		return files != null && !excluded.contains(pkg) && files.containsKey(className.replace('.', '/') + ".class");
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
