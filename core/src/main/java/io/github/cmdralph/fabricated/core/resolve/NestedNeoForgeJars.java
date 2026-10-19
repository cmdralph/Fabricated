package io.github.cmdralph.fabricated.core.resolve;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;

/**
 * The mods.toml of the jars a NeoForge mod file nests (jar-in-jar, listed in its {@code META-INF/jarjar/metadata.json}).
 * NeoForge's discovery finds those mods only after Fabricated's has run, yet Fabric mods may depend on them or be
 * replaced by them (Sodium for NeoForge nests its mod in a service jar). They are read in place, nothing is extracted.
 */
public final class NestedNeoForgeJars {
	/** The jar-in-jar list of a NeoForge mod file. */
	public static final String METADATA = "META-INF/jarjar/metadata.json";
	private static final String MODS_TOML = "META-INF/neoforge.mods.toml";
	private static final String MANIFEST = "META-INF/MANIFEST.MF";
	/** Bounds each text file read: a mods.toml, a manifest or a jar-in-jar list is a few kilobytes. */
	private static final int MAX_TEXT_BYTES = 1 << 20;
	/** Bounds the nested jars opened for one mod file, at every depth. */
	private static final int MAX_JARS = 256;

	/**
	 * One nested jar's mods.toml.
	 *
	 * @param origin     where the jar is, for messages ({@code outer.jar!/META-INF/jarjar/inner.jar})
	 * @param jarVersion what the mods.toml's {@code ${file.jarVersion}} stands for: the jar's manifest
	 *                   Implementation-Version, else the version the jar-in-jar list gives it, else {@code 0}
	 */
	public record ModsToml(String origin, String text, String jarVersion) {
	}

	private final ArchiveLimits limits;
	private final List<ModsToml> found = new ArrayList<>();
	private int opened;

	private NestedNeoForgeJars(ArchiveLimits limits) {
		this.limits = limits;
	}

	/** The mods.toml of every jar nested in {@code jar}, down to the limits' nesting depth. */
	public static List<ModsToml> read(Path jar, ArchiveLimits limits) throws IOException, JsonException {
		if (Files.size(jar) > limits.maxJarBytes()) throw new IOException(jar + " is larger than " + limits.maxJarBytes() + " bytes");
		NestedNeoForgeJars reader = new NestedNeoForgeJars(limits);
		try (FileSystem archive = open(jar)) {
			reader.readNested(archive, String.valueOf(jar.getFileName()), 1);
		}
		return List.copyOf(reader.found);
	}

	private void readNested(FileSystem jar, String origin, int depth) throws IOException, JsonException {
		String metadata = text(jar, METADATA);
		if (metadata == null) return;
		for (Object entry : Json.asList(Json.parseObject(metadata).get("jars"))) {
			if (!(entry instanceof Map<?, ?> listed) || !(listed.get("path") instanceof String path)) continue;
			// A zip file system's paths stay inside it, whatever the list says.
			Path nested = jar.getPath(path);
			if (!Files.isRegularFile(nested)) continue;
			String nestedOrigin = origin + "!/" + path;
			if (++opened > MAX_JARS) throw new IOException(origin + " nests more than " + MAX_JARS + " jars");
			// Opening it reads it whole into memory.
			if (Files.size(nested) > limits.maxEntryBytes()) throw new IOException(nestedOrigin + " is larger than " + limits.maxEntryBytes() + " bytes");
			try (FileSystem inner = open(nested)) {
				String toml = text(inner, MODS_TOML);
				if (toml != null) found.add(new ModsToml(nestedOrigin, toml, jarVersion(inner, listed)));
				if (depth < limits.maxNestingDepth()) readNested(inner, nestedOrigin, depth + 1);
			}
		}
	}

	private static FileSystem open(Path jar) throws IOException {
		try {
			return FileSystems.newFileSystem(jar);
		} catch (ProviderNotFoundException e) {
			throw new IOException(jar + " is not a jar", e);
		}
	}

	private static String jarVersion(FileSystem jar, Map<?, ?> listed) throws IOException {
		String manifest = text(jar, MANIFEST);
		if (manifest != null) {
			String version = new Manifest(new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8))).getMainAttributes().getValue("Implementation-Version");
			if (version != null) return version;
		}
		if (listed.get("version") instanceof Map<?, ?> version && version.get("artifactVersion") instanceof String artifactVersion) return artifactVersion;
		return "0";
	}

	private static String text(FileSystem jar, String name) throws IOException {
		Path path = jar.getPath(name);
		if (!Files.isRegularFile(path)) return null;
		try (InputStream in = Files.newInputStream(path)) {
			byte[] data = in.readNBytes(MAX_TEXT_BYTES + 1);
			if (data.length > MAX_TEXT_BYTES) throw new IOException(name + " is larger than " + MAX_TEXT_BYTES + " bytes");
			return new String(data, StandardCharsets.UTF_8);
		}
	}
}
