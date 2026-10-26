package io.github.cmdralph.fabricated.loader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.Pipeline;
import io.github.cmdralph.fabricated.core.archive.Hashing;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.remap.MappingComposer;
import io.github.cmdralph.fabricated.core.remap.MappingException;
import io.github.cmdralph.fabricated.core.remap.MappingTable;
import net.minecraftforge.fml.loading.FMLLoader;

/**
 * Builds the intermediary → Mojang table for obfuscated Minecraft versions (1.21.11) entirely offline: Fabric's
 * Intermediary (bundled, CC0) composed with Mojang's mappings, which Forge 61's installer leaves among the libraries
 * as TSRG ({@link ForgeLibraries}). Nothing is downloaded and Mojang's mappings are never redistributed. TSRG gives
 * fields no descriptors, so fields sharing an obfuscated name are told apart by their descriptors in the installed
 * game in Mojang's names ({@code -official.jar}). The composed table is cached, keyed by the hashes of both inputs.
 */
final class IntermediaryMappings {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	static final String SYSTEM_PROPERTY = "fabricated.mojmapsPath";

	private IntermediaryMappings() {
	}

	static Pipeline.Mappings load(TranslationCache cache, String minecraftVersion, boolean client, String configuredPath, Path gameJar)
			throws MappingException, IOException {
		Path mojang = locateMojangMappings(minecraftVersion, client, configuredPath, gameJar);
		// Intermediary is fixed per Minecraft version and Fabricated build (which bundles it and composes the table), and
		// the Mojang file's hash is memoised: a warm launch reads neither mapping file.
		String hash = Hashing.sha256("intermediary-" + minecraftVersion + "|" + FabricatedInfo.get().fabricatedVersion() + "|"
				+ cache.sha256(mojang) + "|" + client).substring(0, 40);
		String blob = "mappings-" + minecraftVersion.replace('.', '_') + "-" + hash + ".bin";

		Optional<byte[]> cached = cache.readBlob(blob);
		if (cached.isPresent()) {
			try {
				return new Pipeline.Mappings(MappingTable.deserialize(cached.get()), hash);
			} catch (IOException e) {
				// Corrupt cache file: rebuild below.
			}
		}
		byte[] intermediary = FabricatedInfo.readOptional("intermediary.tiny");
		if (intermediary == null) {
			throw new MappingException("this Fabricated build does not include Intermediary mappings for " + minecraftVersion);
		}
		MappingTable table;
		Path official = mojang.resolveSibling(mojang.getFileName().toString().replace("-mappings.tsrg", "-official.jar"));
		try (Reader tiny = new InputStreamReader(new ByteArrayInputStream(intermediary), StandardCharsets.UTF_8);
				Reader mappings = Files.newBufferedReader(mojang, StandardCharsets.UTF_8);
				ZipFile game = mojang.toString().endsWith(".tsrg") && Files.isRegularFile(official) ? new ZipFile(official.toFile()) : null) {
			table = MappingComposer.compose(tiny, mappings, game == null ? MappingComposer.FieldDescriptors.NONE : fieldDescriptors(game));
		}
		if (table.classCount() == 0) {
			throw new MappingException("the composed mapping table is empty; " + mojang + " does not match Minecraft " + minecraftVersion);
		}
		try {
			cache.storeBlob(blob, table.serialize());
		} catch (IOException e) {
			// Only the next launch's head start: this one has its table.
			LOGGER.warn("Could not cache the composed mappings; they are composed again on the next launch", e);
		}
		return new Pipeline.Mappings(table, hash);
	}

	/**
	 * Candidates, in order: the config value, the {@value #SYSTEM_PROPERTY} system property, then in each libraries
	 * directory ({@link ForgeLibraries#directories}) {@code net/minecraft/<side>/<mc>/<side>-<mc>-mappings.txt}, as
	 * NeoForge's installer names it, the same with the MCP version after the Minecraft version, as Forge's installers
	 * named it up to 1.20.1, and {@code <side>-<mc>-mappings.tsrg}, as Forge 61's installer leaves it.
	 *
	 * @param gameFile a file of the game as Forge runs it, or null
	 */
	static Path locateMojangMappings(String minecraftVersion, boolean client, String configuredPath, Path gameFile) throws MappingException {
		List<String> tried = new ArrayList<>();
		for (String candidate : new String[] {configuredPath, System.getProperty(SYSTEM_PROPERTY)}) {
			if (candidate == null || candidate.isBlank()) continue;
			try {
				Path p = Path.of(candidate);
				if (Files.isRegularFile(p)) return p;
				tried.add(p.toString());
			} catch (InvalidPathException e) {
				tried.add(candidate + " (not a valid path)");
			}
		}
		String mcp = FMLLoader.versionInfo().mcpVersion();
		for (Path libraries : ForgeLibraries.directories(gameFile)) {
			List<Path> candidates = new ArrayList<>(List.of(ForgeLibraries.gameFile(libraries, client, minecraftVersion, "mappings.txt")));
			if (mcp != null && !mcp.isBlank()) candidates.add(ForgeLibraries.gameFile(libraries, client, minecraftVersion + "-" + mcp, "mappings.txt"));
			candidates.add(ForgeLibraries.gameFile(libraries, client, minecraftVersion, "mappings.tsrg"));
			for (Path p : candidates) {
				if (Files.isRegularFile(p)) return p;
				tried.add(p.toString());
			}
		}
		throw new MappingException("Mojang's " + (client ? "client" : "server") + " mapping file for " + minecraftVersion + " was not found (looked at "
				+ String.join(", ", tried) + ")");
	}

	/** The fields' descriptors in the game in Mojang's names, read a class at a time as the composer asks. */
	private static MappingComposer.FieldDescriptors fieldDescriptors(ZipFile game) {
		Map<String, Map<String, String>> classes = new HashMap<>();
		return (owner, name) -> {
			Map<String, String> fields = classes.get(owner);
			if (fields == null) classes.put(owner, fields = fields(game, owner));
			return fields.get(name);
		};
	}

	private static Map<String, String> fields(ZipFile game, String owner) throws IOException {
		ZipEntry entry = game.getEntry(owner + ".class");
		if (entry == null) return Map.of();
		byte[] data;
		try (InputStream in = game.getInputStream(entry)) {
			data = in.readAllBytes();
		}
		Map<String, String> fields = new HashMap<>();
		new ClassReader(data).accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
				fields.put(name, descriptor);
				return null;
			}
		}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return fields;
	}
}
