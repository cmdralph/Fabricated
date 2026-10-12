package io.github.cmdralph.fabricated.core.translate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.github.cmdralph.fabricated.core.archive.ArchiveException;
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
 * would collide.
 *
 * <ol>
 *   <li>Packages owned by the platform (NeoForge, Minecraft, libraries, Fabricated) are filtered out of
 *       Fabric jars: the platform's copy is used.</li>
 *   <li>When a package already belongs to an earlier Fabric mod (load order), the later mod's copy is
 *       filtered out if every file it has there is byte-identical to the earlier copy.</li>
 *   <li>If the copies differ but the later mod has only resources there, its resources are hidden with a
 *       warning (they are almost never loaded through the class loader).</li>
 *   <li>If they differ and the later mod has classes there, it is disabled with an explanation.</li>
 * </ol>
 */
public final class PackageGuard {
	/** Decision for one mod. */
	public record Decision(Set<String> excludedPackages, List<String> notes) {
	}

	/** A mod that cannot load because of a conflicting package. */
	public record Conflict(TranslatedMod mod, String reason, String suggestion) {
	}

	/** Result: per NeoForge id, the packages to filter; and conflicting mods. */
	public record Result(Map<String, Decision> decisions, List<Conflict> conflicts) {
	}

	/** Files of one package in one jar: file name → whether it is a class. */
	private record PackageContents(Path jar, Map<String, Boolean> files) {
		boolean hasClasses() {
			return files.containsValue(Boolean.TRUE);
		}
	}

	private final ArchiveLimits limits;

	public PackageGuard(ArchiveLimits limits) {
		this.limits = limits;
	}

	/**
	 * @param mods             translated mods in load order
	 * @param platformPackages packages (internal form, e.g. {@code net/minecraft/world}) owned by the platform
	 */
	public Result check(List<TranslatedMod> mods, Set<String> platformPackages) {
		Map<String, Decision> decisions = new LinkedHashMap<>();
		List<Conflict> conflicts = new ArrayList<>();
		Map<String, String> owner = new HashMap<>();
		Map<String, PackageContents> ownerContents = new HashMap<>();

		for (TranslatedMod mod : mods) {
			Map<String, PackageContents> packages;
			try {
				packages = packagesOf(mod.jar());
			} catch (ArchiveException | IOException e) {
				conflicts.add(new Conflict(mod, "could not inspect its contents: " + e.getMessage(), "Re-download the mod."));
				continue;
			}
			Set<String> excluded = new TreeSet<>();
			List<String> notes = new ArrayList<>();
			String conflict = null;
			for (Map.Entry<String, PackageContents> pkg : packages.entrySet()) {
				String name = pkg.getKey();
				String dotted = name.replace('/', '.');
				if (platformPackages.contains(name) || PlatformLibraries.isProvided(name + "/")) {
					excluded.add(name);
					notes.add("bundles package " + dotted + ", which the platform already provides; using the platform's copy");
					continue;
				}
				String existing = owner.get(name);
				if (existing == null) continue;
				PackageContents ours = pkg.getValue();
				if (identicalSubset(ownerContents.get(name), ours)) {
					excluded.add(name);
					notes.add("shares identical files in package " + dotted + " with " + existing + "; using " + existing + "'s copy");
				} else if (!ours.hasClasses()) {
					excluded.add(name);
					notes.add("has resources in " + dotted + " that clash with " + existing + "; this mod's copies are hidden");
				} else {
					conflict = "contains package " + dotted + ", which " + existing + " also contains with different classes";
					break;
				}
			}
			if (conflict != null) {
				conflicts.add(new Conflict(mod, conflict, "Java modules cannot share packages. Remove one of the two mods, or ask the authors to relocate (shade) the shared library."));
				continue;
			}
			for (Map.Entry<String, PackageContents> pkg : packages.entrySet()) {
				if (excluded.contains(pkg.getKey())) continue;
				owner.putIfAbsent(pkg.getKey(), mod.neoForgeId());
				ownerContents.putIfAbsent(pkg.getKey(), pkg.getValue());
			}
			decisions.put(mod.neoForgeId(), new Decision(Set.copyOf(excluded), List.copyOf(notes)));
		}
		return new Result(decisions, conflicts);
	}

	private Map<String, PackageContents> packagesOf(Path jar) throws ArchiveException, IOException {
		Map<String, Map<String, Boolean>> packages = new TreeMap<>();
		try (SafeArchive archive = SafeArchive.open(jar, limits.forTranslatedJars())) {
			for (String name : archive.entryNames()) {
				if (name.endsWith("/") || name.startsWith("META-INF/") || name.startsWith("assets/") || name.startsWith("data/")) continue;
				if (name.endsWith("module-info.class")) continue;
				int slash = name.lastIndexOf('/');
				if (slash < 1) continue; // default package
				String pkg = name.substring(0, slash);
				if (!isPackageName(pkg)) continue;
				packages.computeIfAbsent(pkg, k -> new TreeMap<>()).put(name, name.endsWith(".class"));
			}
		}
		Map<String, PackageContents> ret = new TreeMap<>();
		packages.forEach((k, v) -> ret.put(k, new PackageContents(jar, v)));
		return ret;
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

	/** Whether every file in {@code ours} exists in {@code theirs} with identical bytes (hashed lazily). */
	private boolean identicalSubset(PackageContents theirs, PackageContents ours) {
		if (!theirs.files().keySet().containsAll(ours.files().keySet())) return false;
		try {
			Map<String, String> a = hashes(theirs.jar(), ours.files().keySet());
			Map<String, String> b = hashes(ours.jar(), ours.files().keySet());
			return a.equals(b);
		} catch (ArchiveException | IOException e) {
			return false;
		}
	}

	private Map<String, String> hashes(Path jar, Set<String> names) throws ArchiveException, IOException {
		Map<String, String> ret = new TreeMap<>();
		try (SafeArchive archive = SafeArchive.open(jar, limits.forTranslatedJars())) {
			for (String name : names) {
				byte[] data = archive.read(name);
				ret.put(name, data == null ? "" : Hashing.sha256(data));
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
