package io.github.cmdralph.fabricated.core.resolve;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;

/**
 * Libraries that NeoForge and Minecraft already provide. Fabric mods frequently nest or shade copies of
 * them; loading a second copy into the module layer would be a fatal split package, so bundled copies
 * are dropped and the platform's version is used instead.
 */
public final class PlatformLibraries {
	/** Fabric ids (from Loom-generated metadata or real mods) that the platform provides. */
	public static final Set<String> PROVIDED_IDS = Set.of(
			"mixinextras", "mixin_extras", "fabricloader", "fabric-loader", "minecraft", "java", "sponge-mixin");

	/** Package prefixes (internal form) owned by platform libraries. */
	public static final List<String> PROVIDED_PACKAGES = List.of(
			"com/llamalad7/mixinextras/",
			"org/spongepowered/asm/",
			"org/objectweb/asm/",
			"net/fabricmc/loader/",
			// Fabricated's API: a mod that shaded a copy must use the real one.
			"io/github/cmdralph/fabricated/api/",
			"net/fabricmc/api/",
			"com/google/gson/",
			"com/google/common/",
			"org/slf4j/",
			"org/apache/logging/log4j/",
			"org/apache/commons/lang3/",
			"org/apache/commons/io/",
			"it/unimi/dsi/fastutil/",
			"com/mojang/brigadier/",
			"com/mojang/datafixers/",
			"com/mojang/serialization/",
			"com/mojang/authlib/",
			"com/mojang/logging/",
			"com/mojang/jtracy/",
			"io/netty/",
			"org/lwjgl/",
			"org/joml/",
			// NeoForge ships night-config's core and TOML modules only: a mod's JSON or YAML module is its own.
			"com/electronwill/nightconfig/core/",
			"com/electronwill/nightconfig/toml/",
			"net/neoforged/",
			"cpw/mods/",
			"joptsimple/");

	private PlatformLibraries() {
	}

	/** Whether a class/resource path (internal form, e.g. {@code com/google/gson/Gson.class}) is platform-owned. */
	public static boolean isProvided(String path) {
		for (String prefix : PROVIDED_PACKAGES) {
			if (path.startsWith(prefix)) return true;
		}
		return false;
	}

	/**
	 * The version of MixinExtras a jar nests, from its jar-in-jar {@code META-INF/jarjar/metadata.json} (NeoForge nests
	 * {@code mixinextras-neoforge} in its own jar), or {@code null} when it nests none.
	 */
	public static String nestedMixinExtras(String jarJarMetadata) throws JsonException {
		for (Object jar : Json.asList(Json.parseObject(jarJarMetadata).get("jars"))) {
			String version = mixinExtrasVersion(jar);
			if (version != null) return version;
		}
		return null;
	}

	/**
	 * The version of MixinExtras Forge offers its jar-in-jar locator from its own libraries, from its loader's
	 * {@code jarjar_options.json} (Forge 61 and later list {@code mixinextras-forge} there, which the locator adds once a
	 * mod file declares mixin configs), or {@code null} when it offers none.
	 */
	public static String offeredMixinExtras(String jarJarOptions) throws JsonException {
		for (Object option : Json.asList(Json.parseObject(jarJarOptions).get("options"))) {
			String version = option instanceof Map<?, ?> entry ? mixinExtrasVersion(entry.get("meta")) : null;
			if (version != null) return version;
		}
		return null;
	}

	/** The version of a jar-in-jar entry (identifier and version) that is MixinExtras, else null. */
	private static String mixinExtrasVersion(Object jar) {
		if (!(jar instanceof Map<?, ?> entry) || !(entry.get("identifier") instanceof Map<?, ?> id)
				|| !(entry.get("version") instanceof Map<?, ?> version)) return null;
		return "io.github.llamalad7".equals(id.get("group")) && id.get("artifact") instanceof String artifact
				&& artifact.startsWith("mixinextras") && version.get("artifactVersion") instanceof String v ? v : null;
	}
}
