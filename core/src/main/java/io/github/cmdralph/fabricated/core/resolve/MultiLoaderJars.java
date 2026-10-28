package io.github.cmdralph.fabricated.core.resolve;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;

/**
 * Jars with both Fabric's {@code fabric.mod.json} and NeoForge's {@code META-INF/neoforge.mods.toml} (MULTI-1). A
 * multi-loader jar is the NeoForge mod it declares too, and NeoForge loads it. A Fabric build that kept its NeoForge
 * build's metadata isn't: Do a Barrel Roll's 26.x Fabric jars declare a NeoForge mod with the Fabric build's mixins
 * and no class NeoForge starts, so NeoForge applied mixins written for Fabric and the game stopped. Such a jar is a
 * Fabric mod. So is one whose NeoForge metadata is a placeholder for Sinytra Connector ({@code "connector:placeholder"
 * = true} in its properties, a dependency on {@code connector}): Continuity's and LambDynamicLights' NeoForge jars are
 * their Fabric builds, which only Connector, or Fabricated, loads.
 */
public final class MultiLoaderJars {
	public static final String MODS_TOML = "META-INF/neoforge.mods.toml";
	/** NeoForge's {@code @Mod}, which marks the class it starts a Java mod with. */
	private static final byte[] MOD_ANNOTATION = "Lnet/neoforged/fml/common/Mod;".getBytes(StandardCharsets.UTF_8);
	private static final Pattern JAVA_MOD_LOADER = Pattern.compile("(?m)^[ \\t]*modLoader[ \\t]*=[ \\t]*[\"']javafml[\"']");
	private static final Pattern CONNECTOR_PLACEHOLDER = Pattern.compile("(?m)^[ \\t]*[\"']connector:placeholder[\"'][ \\t]*=[ \\t]*true\\b");
	/** Why a jar with both loaders' metadata is a Fabric mod, for the log. */
	public static final String KEPT_METADATA = "it declares a NeoForge mod too, but no class of it for NeoForge to start (MULTI-1)";
	public static final String PLACEHOLDER = "its NeoForge metadata is a placeholder for Sinytra Connector, which loads its Fabric build (MULTI-1)";

	private MultiLoaderJars() {
	}

	/**
	 * Why a jar with NeoForge's metadata is a Fabric mod ({@link #KEPT_METADATA}, {@link #PLACEHOLDER}), or null when
	 * NeoForge should load it as its own mod: it has Fabric's metadata too only when the NeoForge side starts something
	 * of its own (a class with NeoForge's {@code @Mod}, or a language loader other than Java's) or the Fabric side
	 * nothing (no entrypoints: the same mixins load either way). An unreadable jar is left to NeoForge, which reports it;
	 * a jar without NeoForge's metadata is a Fabric mod with no reason to give.
	 */
	public static String fabricReason(Path jar, ArchiveLimits limits) {
		try (SafeArchive archive = SafeArchive.open(jar, limits)) {
			if (!archive.contains(MODS_TOML)) return "";
			if (!archive.contains("fabric.mod.json")) return null;
			String modsToml = archive.readText(MODS_TOML);
			if (CONNECTOR_PLACEHOLDER.matcher(modsToml).find()) return PLACEHOLDER;
			if (!JAVA_MOD_LOADER.matcher(modsToml).find()) return null;
			if (!hasEntrypoints(archive.readText("fabric.mod.json"))) return null;
			for (String name : archive.classEntryNames()) {
				byte[] data = archive.read(name);
				if (data != null && Annotations.mentions(data, MOD_ANNOTATION)) return null;
			}
			return KEPT_METADATA;
		} catch (IOException | RuntimeException e) {
			return null;
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
}
