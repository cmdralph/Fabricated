package io.github.cmdralph.fabricated.core.translate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
 *   <li>If both have a class of the same name with different bytes, or the later mod declares a service
 *       provider in the package (its module would no longer have the provider), it is disabled with an
 *       explanation.</li>
 * </ol>
 */
public final class PackageGuard {
	/**
	 * Decision for one mod.
	 *
	 * @param excludedPackages packages filtered out of its jar and overlay
	 * @param borrowed         files of later mods its module serves too, by the jar they are read from: their part of a
	 *                         package split with this mod
	 */
	public record Decision(Set<String> excludedPackages, Map<Path, Set<String>> borrowed, List<String> notes) {
	}

	/** A mod that cannot load because of a conflicting package. */
	public record Conflict(TranslatedMod mod, String reason, String suggestion) {
	}

	/** Result: per NeoForge id, the packages to filter; and conflicting mods. */
	public record Result(Map<String, Decision> decisions, List<Conflict> conflicts) {
	}

	/**
	 * What a mod has in its packages.
	 *
	 * @param packages         package → its files, each with the jar it is read from (the overlay, or the mod's jar)
	 * @param providerPackages the packages of the service providers it declares in {@code META-INF/services}
	 */
	private record Contents(Map<String, Map<String, Path>> packages, Set<String> providerPackages) {
	}

	private static final String SERVICES = "META-INF/services/";
	private static final String SUGGESTION = "Java modules cannot share packages. Remove one of the two mods, or ask the authors to relocate (shade) the shared library.";

	private final ArchiveLimits limits;

	public PackageGuard(ArchiveLimits limits) {
		this.limits = limits;
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
		Map<String, Set<String>> excludedById = new LinkedHashMap<>();
		Map<String, List<String>> notesById = new HashMap<>();
		Map<String, Map<Path, Set<String>>> borrowedById = new HashMap<>();

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
				if (platformPackages.contains(name) || PlatformLibraries.isProvided(name + "/")) {
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
				if (hasClasses && contents.providerPackages().contains(name)) {
					conflict = "contains package " + dotted + ", which " + existing + " also contains, and declares a service provider in it";
					break;
				}
				if (extra.isEmpty() && differing.isEmpty()) {
					excluded.add(name);
					notes.add("shares identical files in package " + dotted + " with " + existing + "; using " + existing + "'s copy");
				} else if (!hasClasses) {
					excluded.add(name);
					notes.add("has resources in " + dotted + " that clash with " + existing + "; this mod's copies are hidden");
				} else if (differing.stream().anyMatch(f -> f.endsWith(".class"))) {
					String clash = differing.stream().filter(f -> f.endsWith(".class")).findFirst().orElseThrow();
					conflict = "contains package " + dotted + ", which " + existing + " also contains with a different " + clash;
					break;
				} else {
					excluded.add(name);
					lent.put(name, extra);
					notes.add("shares package " + dotted + " with " + existing + ", whose module also serves this mod's " + extra.size() + " file(s) there"
							+ (differing.isEmpty() ? "" : "; " + differing.size() + " clashing resource(s) use " + existing + "'s copies"));
				}
			}
			if (conflict != null) {
				conflicts.add(new Conflict(mod, conflict, SUGGESTION));
				continue;
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
			notesById.put(mod.neoForgeId(), notes);
		}
		Map<String, Decision> decisions = new LinkedHashMap<>();
		excludedById.forEach((id, excluded) -> {
			Map<Path, Set<String>> borrowed = new LinkedHashMap<>();
			borrowedById.getOrDefault(id, Map.of()).forEach((jar, files) -> borrowed.put(jar, Set.copyOf(files)));
			decisions.put(id, new Decision(Set.copyOf(excluded), Collections.unmodifiableMap(borrowed), List.copyOf(notesById.get(id))));
		});
		return new Result(decisions, conflicts);
	}

	/** The mod's files by package (its overlay's copies over its jar's), and the packages of its service providers. */
	private Contents contentsOf(TranslatedMod mod) throws IOException {
		Map<String, Map<String, Path>> packages = new TreeMap<>();
		Set<String> providerPackages = new HashSet<>();
		for (Path jar : mod.overlay() != null && Files.exists(mod.overlay()) ? List.of(mod.jar(), mod.overlay()) : List.of(mod.jar())) {
			try (SafeArchive archive = SafeArchive.open(jar, limits.forTranslatedJars())) {
				for (String name : archive.entryNames()) {
					if (name.startsWith(SERVICES) && !name.endsWith("/")) {
						providerPackages.addAll(providerPackages(archive.readText(name)));
						continue;
					}
					if (name.endsWith("/") || name.startsWith("META-INF/") || name.startsWith("assets/") || name.startsWith("data/")) continue;
					if (name.endsWith("module-info.class")) continue;
					int slash = name.lastIndexOf('/');
					if (slash < 1) continue; // default package
					String pkg = name.substring(0, slash);
					if (!isPackageName(pkg)) continue;
					packages.computeIfAbsent(pkg, k -> new TreeMap<>()).put(name, jar);
				}
			}
		}
		return new Contents(packages, providerPackages);
	}

	/** The packages (internal form) of the providers a service file lists, as NeoForge reads it. */
	static Set<String> providerPackages(String serviceFile) {
		Set<String> packages = new HashSet<>();
		for (String line : serviceFile.split("\\R")) {
			int comment = line.indexOf('#');
			String provider = (comment >= 0 ? line.substring(0, comment) : line).trim();
			int dot = provider.lastIndexOf('.');
			if (dot > 0) packages.add(provider.substring(0, dot).replace('.', '/'));
		}
		return packages;
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
