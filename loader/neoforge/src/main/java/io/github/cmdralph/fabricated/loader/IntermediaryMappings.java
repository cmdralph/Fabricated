package io.github.cmdralph.fabricated.loader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.Pipeline;
import io.github.cmdralph.fabricated.core.archive.Hashing;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.remap.MappingComposer;
import io.github.cmdralph.fabricated.core.remap.MappingException;
import io.github.cmdralph.fabricated.core.remap.MappingTable;
import net.neoforged.fml.loading.LibraryFinder;

/**
 * Builds the intermediary → Mojang table for obfuscated Minecraft versions entirely offline:
 * Fabric's Intermediary (bundled, CC0) is composed with Mojang's official mapping file that the NeoForge
 * installer already placed in the libraries directory. Nothing is downloaded and Mojang's mappings are
 * never redistributed. The composed table is cached, keyed by the hashes of both inputs.
 */
final class IntermediaryMappings {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	static final String SYSTEM_PROPERTY = "fabricated.mojmapsPath";

	private IntermediaryMappings() {
	}

	static Pipeline.Mappings load(TranslationCache cache, String minecraftVersion, boolean client, String configuredPath)
			throws MappingException, IOException {
		Path mojang = locateMojangMappings(minecraftVersion, client, configuredPath);
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
		try (Reader tiny = new InputStreamReader(new ByteArrayInputStream(intermediary), StandardCharsets.UTF_8);
				Reader proguard = Files.newBufferedReader(mojang, StandardCharsets.UTF_8)) {
			table = MappingComposer.compose(tiny, proguard);
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
	 * Candidates, in order: the config value, the {@value #SYSTEM_PROPERTY} system property, and the
	 * NeoForge installer's location {@code <libraries>/net/minecraft/<side>/<mc>/<side>-<mc>-mappings.txt}, or with the
	 * NeoForm build after the Minecraft version ({@code <mc>-<neoform>}) where the installer names the folder so (NeoForge
	 * 21.1).
	 */
	static Path locateMojangMappings(String minecraftVersion, boolean client, String configuredPath) throws MappingException {
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
		String side = client ? "client" : "server";
		String withNeoForm = Fml.minecraftAndNeoFormVersion();
		for (String version : withNeoForm.equals(minecraftVersion) ? List.of(minecraftVersion) : List.of(minecraftVersion, withNeoForm)) {
			try {
				Path p = LibraryFinder.findPathForMaven("net.minecraft", side, "txt", "mappings", version);
				if (Files.isRegularFile(p)) return p;
				tried.add(p.toString());
			} catch (IllegalStateException e) {
				tried.add("(libraries directory unknown: " + e.getMessage() + ")");
				break;
			}
		}
		throw new MappingException("Mojang's " + side + " mapping file for " + minecraftVersion + " was not found (looked at " + String.join(", ", tried) + ")");
	}
}
