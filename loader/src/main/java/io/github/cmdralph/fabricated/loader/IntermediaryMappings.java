package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
	static final String SYSTEM_PROPERTY = "fabricated.mojmapsPath";

	private IntermediaryMappings() {
	}

	static Pipeline.Mappings load(TranslationCache cache, String minecraftVersion, boolean client, String configuredPath)
			throws MappingException, IOException {
		byte[] intermediary = FabricatedInfo.readOptional("intermediary.tiny");
		if (intermediary == null) {
			throw new MappingException("this Fabricated build does not include Intermediary mappings for " + minecraftVersion);
		}
		Path mojang = locateMojangMappings(minecraftVersion, client, configuredPath);
		byte[] mojangBytes = Files.readAllBytes(mojang);
		String hash = Hashing.sha256(Hashing.sha256(intermediary) + "|" + Hashing.sha256(mojangBytes) + "|" + client).substring(0, 40);
		String blob = "mappings-" + minecraftVersion.replace('.', '_') + "-" + hash + ".bin";

		Optional<byte[]> cached = cache.readBlob(blob);
		if (cached.isPresent()) {
			try {
				return new Pipeline.Mappings(MappingTable.deserialize(cached.get()), hash);
			} catch (IOException e) {
				// Corrupt cache file: rebuild below.
			}
		}
		MappingTable table;
		try (Reader tiny = new InputStreamReader(new java.io.ByteArrayInputStream(intermediary), StandardCharsets.UTF_8);
				Reader proguard = new InputStreamReader(new java.io.ByteArrayInputStream(mojangBytes), StandardCharsets.UTF_8)) {
			table = MappingComposer.compose(tiny, proguard);
		}
		if (table.classCount() == 0) {
			throw new MappingException("the composed mapping table is empty; " + mojang + " does not match Minecraft " + minecraftVersion);
		}
		cache.storeBlob(blob, table.serialize());
		return new Pipeline.Mappings(table, hash);
	}

	/**
	 * Candidates, in order: the config value, the {@value #SYSTEM_PROPERTY} system property, and the
	 * NeoForge installer's location {@code <libraries>/net/minecraft/<side>/<mc>/<side>-<mc>-mappings.txt}.
	 */
	static Path locateMojangMappings(String minecraftVersion, boolean client, String configuredPath) throws MappingException {
		List<String> tried = new ArrayList<>();
		for (String candidate : new String[] {configuredPath, System.getProperty(SYSTEM_PROPERTY)}) {
			if (candidate == null || candidate.isBlank()) continue;
			Path p = Path.of(candidate);
			if (Files.isRegularFile(p)) return p;
			tried.add(p.toString());
		}
		String side = client ? "client" : "server";
		try {
			Path p = LibraryFinder.findPathForMaven("net.minecraft", side, "txt", "mappings", minecraftVersion);
			if (Files.isRegularFile(p)) return p;
			tried.add(p.toString());
		} catch (IllegalStateException e) {
			tried.add("(libraries directory unknown: " + e.getMessage() + ")");
		}
		throw new MappingException("Mojang's " + side + " mapping file for " + minecraftVersion + " was not found (looked at " + String.join(", ", tried) + ")");
	}
}
