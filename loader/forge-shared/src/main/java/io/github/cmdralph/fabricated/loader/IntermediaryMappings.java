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
import io.github.cmdralph.fabricated.core.remap.MappingException;
import io.github.cmdralph.fabricated.core.remap.MappingTable;
import net.minecraftforge.fml.loading.FMLLoader;

/**
 * Builds the intermediary → runtime table for an obfuscated Minecraft version entirely offline: Fabric's Intermediary
 * (bundled, CC0) composed with the mappings Forge's installer leaves among the libraries ({@link ForgeLibraries}),
 * Mojang's and those {@link ForgeStack#mappingFiles} adds, as {@link ForgeStack#compose} reads them. Nothing is
 * downloaded and no mapping file is redistributed. The composed table is cached, keyed by the hashes of those files. The
 * places looked at are listed when a file isn't found.
 */
final class IntermediaryMappings {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	static final String SYSTEM_PROPERTY = "fabricated.mojmapsPath";

	private IntermediaryMappings() {
	}

	static Pipeline.Mappings load(TranslationCache cache, String minecraftVersion, boolean client, String configuredPath, Path gameFile)
			throws MappingException, IOException {
		List<Path> files = ForgeStack.mappingFiles(minecraftVersion, locateMojangMappings(minecraftVersion, client, configuredPath, gameFile), gameFile);
		// Intermediary is fixed per Minecraft version and Fabricated build (which bundles it and composes the table), and
		// the files' hashes are memoised: a warm launch reads no mapping file, and reads the table only when a translation
		// or a mod needs it (PERF-2).
		StringBuilder key = new StringBuilder("intermediary-" + minecraftVersion + "|" + FabricatedInfo.get().fabricatedVersion());
		for (Path file : files) key.append('|').append(cache.sha256(file));
		String hash = Hashing.sha256(key + "|" + client).substring(0, 40);
		String blob = "mappings-" + minecraftVersion.replace('.', '_') + "-" + hash + ".bin";
		cache.keepBlob(blob);
		return new Pipeline.Mappings(() -> table(cache, minecraftVersion, files, blob), hash);
	}

	/** The table, from the cache's blob, else composed from Intermediary and the files and cached. */
	private static MappingTable table(TranslationCache cache, String minecraftVersion, List<Path> files, String blob) throws MappingException, IOException {
		Optional<byte[]> cached = cache.readBlob(blob);
		if (cached.isPresent()) {
			try {
				return MappingTable.deserialize(cached.get());
			} catch (IOException e) {
				// Corrupt cache file: rebuild below.
			}
		}
		byte[] intermediary = FabricatedInfo.readOptional("intermediary.tiny");
		if (intermediary == null) {
			throw new MappingException("this Fabricated build does not include Intermediary mappings for " + minecraftVersion);
		}
		MappingTable table;
		try (Reader tiny = new InputStreamReader(new ByteArrayInputStream(intermediary), StandardCharsets.UTF_8)) {
			table = ForgeStack.compose(tiny, files);
		}
		if (table.classCount() == 0) {
			throw new MappingException("the composed mapping table is empty; " + String.join(" and ", files.stream().map(String::valueOf).toList())
					+ (files.size() == 1 ? " does" : " do") + " not match Minecraft " + minecraftVersion);
		}
		try {
			cache.storeBlob(blob, table.serialize());
		} catch (IOException e) {
			// Only the next launch's head start: this one has its table.
			LOGGER.warn("Could not cache the composed mappings; they are composed again on the next launch", e);
		}
		return table;
	}

	/** {@code <mc>-<mcp>}, as Forge's installer names the game's and MCPConfig's libraries; the Minecraft version alone without an MCP version. */
	static String minecraftAndMcpVersion(String minecraftVersion) {
		String mcp = FMLLoader.versionInfo().mcpVersion();
		return mcp == null || mcp.isBlank() ? minecraftVersion : minecraftVersion + "-" + mcp;
	}

	/**
	 * Mojang's mappings. Candidates, in order: the config value, the {@value #SYSTEM_PROPERTY} system property, then in
	 * each libraries directory ({@link ForgeLibraries#directories}) {@code net/minecraft/<side>/<mc>/<side>-<mc>-mappings.txt},
	 * as NeoForge's installer names it, the same with the MCP version after the Minecraft version, as Forge 47's installer
	 * names it, and {@code <side>-<mc>-mappings.tsrg}, as Forge 61's installer leaves it.
	 *
	 * @param gameFile a file of the game as Forge runs it, or null
	 */
	static Path locateMojangMappings(String minecraftVersion, boolean client, String configuredPath, Path gameFile) throws MappingException {
		List<String> tried = new ArrayList<>();
		Path configured = configured(tried, configuredPath, System.getProperty(SYSTEM_PROPERTY));
		if (configured != null) return configured;
		String withMcp = minecraftAndMcpVersion(minecraftVersion);
		for (Path libraries : ForgeLibraries.directories(gameFile)) {
			List<Path> candidates = new ArrayList<>(List.of(ForgeLibraries.gameFile(libraries, client, minecraftVersion, "mappings.txt")));
			if (!withMcp.equals(minecraftVersion)) candidates.add(ForgeLibraries.gameFile(libraries, client, withMcp, "mappings.txt"));
			candidates.add(ForgeLibraries.gameFile(libraries, client, minecraftVersion, "mappings.tsrg"));
			for (Path p : candidates) {
				if (Files.isRegularFile(p)) return p;
				tried.add(p.toString());
			}
		}
		throw new MappingException("Mojang's " + (client ? "client" : "server") + " mapping file for " + minecraftVersion + " was not found (looked at "
				+ String.join(", ", tried) + ")");
	}

	/** The first of the given paths that is a file; the others are added to {@code tried}. */
	static Path configured(List<String> tried, String... candidates) {
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
