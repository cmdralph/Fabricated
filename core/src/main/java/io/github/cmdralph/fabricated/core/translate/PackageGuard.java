package io.github.cmdralph.fabricated.core.translate;

import java.io.IOException;
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
 * Prevents JPMS split packages, which are fatal in NeoForge's module layer.
 *
 * <ol>
 *   <li>Classes in packages owned by the platform (NeoForge, Minecraft, libraries, Fabricated) are
 *       filtered out of Fabric jars: the platform's copy is used.</li>
 *   <li>When two Fabric mods contain the same package, the later mod (in load order) has the package
 *       filtered out if every class it contains there is byte-identical to the earlier mod's copy.</li>
 *   <li>Otherwise the later mod is disabled with an explanation.</li>
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
		// package -> owning mod, its class names, and its jar (hashes are computed only on overlap)
		Map<String, String> owner = new HashMap<>();
		Map<String, Map<String, String>> ownerClasses = new HashMap<>();
		Map<String, java.nio.file.Path> ownerJar = new HashMap<>();

		for (TranslatedMod mod : mods) {
			Map<String, Map<String, String>> packages;
			try {
				packages = classesByPackage(mod);
			} catch (ArchiveException | IOException e) {
				conflicts.add(new Conflict(mod, "could not inspect its classes: " + e.getMessage(), "Re-download the mod."));
				continue;
			}
			Set<String> excluded = new TreeSet<>();
			List<String> notes = new ArrayList<>();
			String conflict = null;
			for (Map.Entry<String, Map<String, String>> pkg : packages.entrySet()) {
				String name = pkg.getKey();
				if (platformPackages.contains(name) || PlatformLibraries.isProvided(name + "/")) {
					excluded.add(name);
					notes.add("bundles package " + name.replace('/', '.') + ", which the platform already provides; using the platform's copy");
					continue;
				}
				String existing = owner.get(name);
				if (existing == null) continue;
				boolean identicalSubset;
				try {
					Map<String, String> theirs = hashes(ownerJar.get(name), ownerClasses.get(name).keySet());
					Map<String, String> ours = hashes(mod.jar(), pkg.getValue().keySet());
					identicalSubset = theirs.entrySet().containsAll(ours.entrySet());
				} catch (ArchiveException | IOException e) {
					identicalSubset = false;
				}
				if (identicalSubset) {
					excluded.add(name);
					notes.add("shares identical classes in package " + name.replace('/', '.') + " with " + existing + "; using " + existing + "'s copy");
				} else {
					conflict = "contains package " + name.replace('/', '.') + ", which " + existing + " also contains with different classes";
					break;
				}
			}
			if (conflict != null) {
				conflicts.add(new Conflict(mod, conflict, "Java modules cannot share packages. Remove one of the two mods, or ask the authors to relocate (shade) the shared library."));
				continue;
			}
			for (Map.Entry<String, Map<String, String>> pkg : packages.entrySet()) {
				if (excluded.contains(pkg.getKey())) continue;
				owner.putIfAbsent(pkg.getKey(), mod.neoForgeId());
				ownerClasses.putIfAbsent(pkg.getKey(), pkg.getValue());
				ownerJar.putIfAbsent(pkg.getKey(), mod.jar());
			}
			decisions.put(mod.neoForgeId(), new Decision(Set.copyOf(excluded), List.copyOf(notes)));
		}
		return new Result(decisions, conflicts);
	}

	private Map<String, Map<String, String>> classesByPackage(TranslatedMod mod) throws ArchiveException, IOException {
		Map<String, Map<String, String>> packages = new TreeMap<>();
		try (SafeArchive archive = SafeArchive.open(mod.jar(), limits)) {
			for (String name : archive.entryNames()) {
				if (!name.endsWith(".class") || name.startsWith("META-INF/") || name.endsWith("module-info.class")) continue;
				int slash = name.lastIndexOf('/');
				if (slash < 0) continue; // default package: not part of any named package
				String pkg = name.substring(0, slash);
				packages.computeIfAbsent(pkg, k -> new TreeMap<>()).put(name, "");
			}
		}
		return packages;
	}

	/** SHA-256 of the given class files; only computed for packages that actually overlap. */
	private Map<String, String> hashes(java.nio.file.Path jar, Set<String> names) throws ArchiveException, IOException {
		Map<String, String> ret = new TreeMap<>();
		try (SafeArchive archive = SafeArchive.open(jar, limits)) {
			for (String name : names) {
				byte[] data = archive.read(name);
				ret.put(name, data == null ? "" : Hashing.sha256(data));
			}
		}
		return ret;
	}

	/** Whether {@code relativePath} lies directly in one of {@code excluded} packages and is a class file. */
	public static boolean isExcluded(String relativePath, Set<String> excluded) {
		if (excluded.isEmpty() || !relativePath.endsWith(".class")) return false;
		int slash = relativePath.lastIndexOf('/');
		return slash > 0 && excluded.contains(relativePath.substring(0, slash));
	}
}
