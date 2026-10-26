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
import net.minecraftforge.fml.loading.FMLLoader;

/**
 * Builds the intermediary → runtime table for Forge 47, whose game has Mojang's class names and SRG member names,
 * entirely offline: Fabric's Intermediary (bundled, CC0) composed with MCPConfig's obfuscated → SRG mappings and with
 * Mojang's mappings ({@link MappingComposer#composeSrg}), both of which Forge's installer leaves among the libraries
 * ({@link ForgeLibraries}). Nothing is downloaded and neither file is redistributed. The composed table is cached, keyed
 * by the hashes of all three inputs. The places looked at are listed when a file isn't found.
 */
final class IntermediaryMappings {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	static final String SYSTEM_PROPERTY = "fabricated.mojmapsPath";
	/** Where MCPConfig's obfuscated → SRG mappings are, for a launcher that keeps them elsewhere. */
	static final String SRG_SYSTEM_PROPERTY = "fabricated.srgMappingsPath";

	private IntermediaryMappings() {
	}

	static Pipeline.Mappings load(TranslationCache cache, String minecraftVersion, boolean client, String configuredPath, Path gameJar)
			throws MappingException, IOException {
		String version = minecraftAndMcpVersion(minecraftVersion);
		Path mojang = locateMojangMappings(minecraftVersion, version, client, configuredPath, gameJar);
		Path srg = locateSrgMappings(minecraftVersion, version, gameJar);
		// Intermediary is fixed per Minecraft version and Fabricated build (which bundles it and composes the table), and
		// the other files' hashes are memoised: a warm launch reads no mapping file.
		String hash = Hashing.sha256("intermediary-srg-" + minecraftVersion + "|" + FabricatedInfo.get().fabricatedVersion() + "|"
				+ cache.sha256(srg) + "|" + cache.sha256(mojang) + "|" + client).substring(0, 40);
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
				Reader srgMappings = Files.newBufferedReader(srg, StandardCharsets.UTF_8);
				Reader mojangMappings = Files.newBufferedReader(mojang, StandardCharsets.UTF_8)) {
			table = MappingComposer.composeSrg(tiny, srgMappings, mojangMappings);
		}
		if (table.classCount() == 0) {
			throw new MappingException("the composed mapping table is empty; " + srg + " and " + mojang + " do not match Minecraft " + minecraftVersion);
		}
		try {
			cache.storeBlob(blob, table.serialize());
		} catch (IOException e) {
			// Only the next launch's head start: this one has its table.
			LOGGER.warn("Could not cache the composed mappings; they are composed again on the next launch", e);
		}
		return new Pipeline.Mappings(table, hash);
	}

	/** {@code <mc>-<mcp>}, as Forge's installer names the game's and MCPConfig's libraries; the Minecraft version alone without an MCP version. */
	static String minecraftAndMcpVersion(String minecraftVersion) {
		String mcp = FMLLoader.versionInfo().mcpVersion();
		return mcp == null || mcp.isBlank() ? minecraftVersion : minecraftVersion + "-" + mcp;
	}

	/**
	 * Mojang's mappings. Candidates, in order: the config value, the {@value #SYSTEM_PROPERTY} system property, then in
	 * each libraries directory ({@link ForgeLibraries#directories}) {@code net/minecraft/<side>/<mc>-<mcp>/<side>-<mc>-<mcp>-mappings.txt},
	 * as Forge's installer names them, and the same with the Minecraft version alone, as NeoForge's names them.
	 */
	static Path locateMojangMappings(String minecraftVersion, String minecraftAndMcpVersion, boolean client, String configuredPath, Path gameJar)
			throws MappingException {
		List<String> tried = new ArrayList<>();
		Path configured = configured(tried, configuredPath, System.getProperty(SYSTEM_PROPERTY));
		if (configured != null) return configured;
		for (Path libraries : ForgeLibraries.directories(gameJar)) {
			for (String version : List.of(minecraftAndMcpVersion, minecraftVersion)) {
				Path p = ForgeLibraries.gameFile(libraries, client, version, "mappings.txt");
				if (Files.isRegularFile(p)) return p;
				tried.add(p.toString());
			}
		}
		throw new MappingException("Mojang's " + (client ? "client" : "server") + " mapping file for " + minecraftVersion + " was not found (looked at "
				+ String.join(", ", tried) + ")");
	}

	/**
	 * MCPConfig's obfuscated → SRG mappings. Candidates, in order: the {@value #SRG_SYSTEM_PROPERTY} system property, then
	 * in each libraries directory {@code de/oceanlabs/mcp/mcp_config/<mc>-<mcp>/mcp_config-<mc>-<mcp>-mappings.txt}, which
	 * Forge's installer extracts from MCPConfig's archive.
	 */
	static Path locateSrgMappings(String minecraftVersion, String minecraftAndMcpVersion, Path gameJar) throws MappingException {
		List<String> tried = new ArrayList<>();
		Path configured = configured(tried, System.getProperty(SRG_SYSTEM_PROPERTY));
		if (configured != null) return configured;
		for (Path libraries : ForgeLibraries.directories(gameJar)) {
			Path p = ForgeLibraries.mcpConfigFile(libraries, minecraftAndMcpVersion, "mappings.txt");
			if (Files.isRegularFile(p)) return p;
			tried.add(p.toString());
		}
		throw new MappingException("MCPConfig's SRG mapping file for " + minecraftVersion + " was not found (looked at " + String.join(", ", tried) + ")");
	}

	/** The first of the given paths that is a file; the others are added to {@code tried}. */
	private static Path configured(List<String> tried, String... candidates) {
		for (String candidate : candidates) {
			if (candidate == null || candidate.isBlank()) continue;
			try {
				Path p = Path.of(candidate);
				if (Files.isRegularFile(p)) return p;
				tried.add(p.toString());
			} catch (InvalidPathException e) {
				tried.add(candidate + " (not a valid path)");
			}
		}
		return null;
	}
}
