package io.github.cmdralph.fabricated.core.translate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import io.github.cmdralph.fabricated.core.archive.ArchiveException;
import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.Hashing;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.bytecode.ClassIndex;
import io.github.cmdralph.fabricated.core.bytecode.LinkageChecker;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;

/**
 * Runs the {@link LinkageChecker} over every translated mod against an index of everything that will
 * exist at runtime (game classes, Fabricated's native Fabric API, and all Fabric jars). Results are
 * cached per mod, keyed by the mod and a fingerprint of the whole mod set.
 */
public final class LinkageAnalysis {
	/** Owners verified: Minecraft, Blaze3D and the Fabric API namespace. */
	public static final Predicate<String> CHECKED = name -> name.startsWith("net/minecraft/")
			|| name.startsWith("com/mojang/blaze3d/") || name.startsWith("net/fabricmc/fabric/");

	private final TranslationCache cache;
	private final ArchiveLimits limits;
	private final boolean clientSide;

	public LinkageAnalysis(TranslationCache cache, ArchiveLimits limits, boolean clientSide) {
		this.cache = cache;
		this.limits = limits;
		this.clientSide = clientSide;
	}

	/**
	 * @param mods         translated mods
	 * @param runtime      classes available at runtime (game + native Fabric API + all Fabric jars)
	 * @param setFingerprint identifies the mod set and game version for cache keys
	 * @return issues per NeoForge id (only mods with issues are present)
	 */
	public Map<String, List<String>> analyse(List<TranslatedMod> mods, ClassBytesSource runtime, String setFingerprint) {
		ClassIndex index = new ClassIndex(runtime);
		LinkageChecker checker = new LinkageChecker(index, CHECKED, clientSide);
		Map<String, List<String>> result = new LinkedHashMap<>();
		for (TranslatedMod mod : mods) {
			String blob = "linkage-" + Hashing.sha256(mod.candidate().sha256() + "|" + setFingerprint + "|" + (clientSide ? "c" : "s")).substring(0, 40) + ".json";
			List<String> issues = readCached(blob);
			if (issues == null) {
				issues = check(mod, checker);
				try {
					cache.storeBlob(blob, Json.write(issues, false).getBytes(StandardCharsets.UTF_8));
				} catch (IOException e) {
					// Caching is an optimisation only.
				}
			}
			if (!issues.isEmpty()) result.put(mod.neoForgeId(), issues);
		}
		return result;
	}

	private List<String> readCached(String blob) {
		try {
			Optional<byte[]> data = cache.readBlob(blob);
			if (data.isEmpty()) return null;
			Object parsed = Json.parse(new String(data.get(), StandardCharsets.UTF_8));
			if (!(parsed instanceof List<?> list)) return null;
			List<String> ret = new ArrayList<>();
			for (Object o : list) ret.add(String.valueOf(o));
			return ret;
		} catch (IOException | JsonException e) {
			return null;
		}
	}

	private List<String> check(TranslatedMod mod, LinkageChecker checker) {
		List<LinkageChecker.Issue> issues = new ArrayList<>();
		try (SafeArchive archive = SafeArchive.open(mod.jar(), limits)) {
			for (String name : archive.entryNames()) {
				if (!name.endsWith(".class") || name.startsWith("META-INF/") || name.endsWith("module-info.class")) continue;
				byte[] data = archive.read(name);
				if (data != null) checker.check(data, issues);
			}
		} catch (ArchiveException | IOException e) {
			return List.of("could not be analysed: " + e.getMessage());
		}
		return issues.stream().map(LinkageChecker.Issue::toString).toList();
	}
}
