package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

import io.github.cmdralph.fabricated.core.translate.TranslationEnvironment.Namespace;

/**
 * Build-time facts baked into the Fabricated jar ({@code META-INF/fabricated/version.properties}) and
 * accessors for its bundled resources.
 *
 * @param fabricatedVersion Fabricated's own version
 * @param minecraftVersion  the Minecraft version this jar was built for
 * @param namespace         how Fabric mods for that version are distributed
 * @param mixinFallback     Mixin version to assume if the runtime's cannot be detected
 */
public record FabricatedInfo(String fabricatedVersion, String minecraftVersion, Namespace namespace, String mixinFallback) {
	public static final String RESOURCE_ROOT = "/META-INF/fabricated/";

	private static volatile FabricatedInfo instance;

	public static FabricatedInfo get() {
		FabricatedInfo info = instance;
		if (info == null) {
			synchronized (FabricatedInfo.class) {
				info = instance;
				if (info == null) {
					info = load();
					instance = info;
				}
			}
		}
		return info;
	}

	private static FabricatedInfo load() {
		Properties p = new Properties();
		try (InputStream in = open("version.properties")) {
			p.load(in);
		} catch (IOException e) {
			throw new UncheckedIOException("Fabricated jar is missing version.properties", e);
		}
		return new FabricatedInfo(
				require(p, "fabricated.version"),
				require(p, "minecraft.version"),
				Namespace.valueOf(require(p, "namespace")),
				require(p, "mixin.fallback"));
	}

	private static String require(Properties p, String key) {
		String v = p.getProperty(key);
		if (v == null || v.isBlank()) throw new IllegalStateException("version.properties is missing " + key);
		return v.trim();
	}

	/** Opens a bundled resource; throws if it is absent. */
	public static InputStream open(String name) throws IOException {
		InputStream in = FabricatedInfo.class.getResourceAsStream(RESOURCE_ROOT + name);
		if (in == null) throw new IOException("Missing bundled resource " + RESOURCE_ROOT + name);
		return in;
	}

	/** Reads a bundled resource fully, or returns {@code null} if absent. */
	public static byte[] readOptional(String name) throws IOException {
		try (InputStream in = FabricatedInfo.class.getResourceAsStream(RESOURCE_ROOT + name)) {
			return in == null ? null : in.readAllBytes();
		}
	}

	/** Whether the running Minecraft version is one this build supports (same major.minor line). */
	public boolean supports(String runningMinecraftVersion) {
		return family(minecraftVersion).equals(family(runningMinecraftVersion));
	}

	static String family(String version) {
		String[] parts = version.split("\\.");
		return parts.length >= 2 ? parts[0] + "." + parts[1] : version;
	}
}
