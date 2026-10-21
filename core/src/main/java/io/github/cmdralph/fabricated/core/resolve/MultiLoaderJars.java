package io.github.cmdralph.fabricated.core.resolve;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;

/**
 * Jars with both Fabric's {@code fabric.mod.json} and NeoForge's {@code META-INF/neoforge.mods.toml} (MULTI-1). A
 * multi-loader jar is the NeoForge mod it declares too, and NeoForge loads it. A Fabric build that kept its NeoForge
 * build's metadata isn't: Do a Barrel Roll's 26.x Fabric jars declare a NeoForge mod with the Fabric build's mixins
 * and no class NeoForge starts, so NeoForge applied mixins written for Fabric and the game stopped. Such a jar is a
 * Fabric mod.
 */
public final class MultiLoaderJars {
	public static final String MODS_TOML = "META-INF/neoforge.mods.toml";
	/** NeoForge's {@code @Mod}, which marks the class it starts a Java mod with. */
	private static final byte[] MOD_ANNOTATION = "Lnet/neoforged/fml/common/Mod;".getBytes(StandardCharsets.UTF_8);
	private static final Pattern JAVA_MOD_LOADER = Pattern.compile("(?m)^\\s*modLoader\\s*=\\s*[\"']javafml[\"']");

	private MultiLoaderJars() {
	}

	/**
	 * Whether NeoForge should load the jar as its own mod: it has NeoForge's metadata, and also Fabric's only when the
	 * NeoForge side starts something of its own (a class with NeoForge's {@code @Mod}, or a language loader other than
	 * Java's) or the Fabric side nothing (no entrypoints: the same mixins load either way). An unreadable jar is left to
	 * NeoForge, which reports it.
	 */
	public static boolean forNeoForge(Path jar, ArchiveLimits limits) {
		try (SafeArchive archive = SafeArchive.open(jar, limits)) {
			if (!archive.contains(MODS_TOML)) return false;
			if (!archive.contains("fabric.mod.json")) return true;
			if (!JAVA_MOD_LOADER.matcher(archive.readText(MODS_TOML)).find()) return true;
			if (!hasEntrypoints(archive.readText("fabric.mod.json"))) return true;
			for (String name : archive.classEntryNames()) {
				if (contains(archive.read(name), MOD_ANNOTATION)) return true;
			}
			return false;
		} catch (IOException | RuntimeException e) {
			return true;
		}
	}

	private static boolean hasEntrypoints(String fabricModJson) {
		try {
			Map<String, Object> json = Json.parseObject(fabricModJson);
			if (!(json.get("entrypoints") instanceof Map<?, ?> entrypoints)) return false;
			return entrypoints.values().stream().anyMatch(v -> v instanceof List<?> list && !list.isEmpty());
		} catch (JsonException e) {
			return false;
		}
	}

	private static boolean contains(byte[] data, byte[] needle) {
		if (data == null) return false;
		outer:
		for (int i = 0; i <= data.length - needle.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (data[i + j] != needle[j]) continue outer;
			}
			return true;
		}
		return false;
	}
}
