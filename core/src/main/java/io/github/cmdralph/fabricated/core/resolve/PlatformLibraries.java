package io.github.cmdralph.fabricated.core.resolve;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;

/**
 * Libraries that every platform (NeoForge, Forge) and Minecraft already provide. Fabric mods frequently nest or shade
 * copies of them; loading a second copy into the module layer would be a fatal split package, so bundled copies are
 * dropped and the platform's version is used instead. The platform loader's own packages are the platform
 * descriptor's ({@code PlatformDescriptor.providedPackages}).
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
			"cpw/mods/",
			"joptsimple/");

	/**
	 * The prefixes of {@link #PROVIDED_PACKAGES} whose library comes in modules the platform ships some of only (the
	 * game's LWJGL bindings, Netty's and Log4j's modules): a Fabric mod may nest another (LWJGL's NanoVG, Netty's HTTP
	 * codec), whose packages aren't the platform's (PLAT-2, as PLAT-1 was for night-config).
	 */
	static final List<String> PARTIAL_FAMILIES = List.of("org/lwjgl/", "io/netty/", "org/apache/logging/log4j/");

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
	 * Whether a mod's package (internal form, e.g. {@code org/lwjgl/nanovg}) is a platform library's. In a family of
	 * {@link #PARTIAL_FAMILIES} it is where the platform's loaders have one of its classes: the family's modules the
	 * platform ships may be on no module layer (the game's LWJGL on FML 10 and later), so its packages aren't all among
	 * those the loader reads from its layers. Elsewhere it is as by {@link #isProvided(String)}.
	 *
	 * @param files           the package's files, by name in the jar (e.g. {@code org/lwjgl/nanovg/NanoVG.class})
	 * @param platformClasses the classes the platform's loaders have outside the game
	 */
	public static boolean isProvided(String pkg, Collection<String> files, ClassBytesSource platformClasses) {
		String path = pkg + "/";
		if (!isProvided(path)) return false;
		for (String family : PARTIAL_FAMILIES) {
			if (!path.startsWith(family)) continue;
			for (String file : files) {
				if (!file.endsWith(".class")) continue;
				try {
					if (platformClasses.read(file.substring(0, file.length() - ".class".length())) != null) return true;
				} catch (IOException e) {
					return true; // the platform has the class, but it couldn't be read: still the platform's package
				}
			}
			return false;
		}
		return true;
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
