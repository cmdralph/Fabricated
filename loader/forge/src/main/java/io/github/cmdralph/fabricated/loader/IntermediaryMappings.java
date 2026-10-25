package io.github.cmdralph.fabricated.loader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
 * Intermediary (bundled, CC0) composed with Mojang's mapping file that the Forge installer left among the libraries.
 * Nothing is downloaded and Mojang's mappings are never redistributed. The composed table is cached, keyed by the
 * hashes of both inputs. Where Forge 61's installer puts the file is UNVERIFIED (docs/PORTING.md §4.1, question 3):
 * the places looked at are listed when it isn't found.
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
	 * Candidates, in order: the config value, the {@value #SYSTEM_PROPERTY} system property, {@code <side>-<mc>-mappings.txt}
	 * next to the game's jar, then in each libraries directory (the {@code libraryDirectory} system property, and the one
	 * Forge's own loader jar is in) {@code net/minecraft/<side>/<mc>/<side>-<mc>-mappings.txt}, as NeoForge's installer
	 * names it, and the same with the MCP version after the Minecraft version, as Forge's installers named it up to
	 * 1.20.1. Forge 61's server installer leaves {@code <side>-<mc>-mappings.tsrg} there instead (the forge branch's
	 * server smoke), whose format Fabricated doesn't read yet: the error names it.
	 *
	 * @param gameJar the game's jar as Forge runs it (among the libraries, next to Mojang's mappings), or null
	 */
	static Path locateMojangMappings(String minecraftVersion, boolean client, String configuredPath, Path gameJar) throws MappingException {
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
		Path gameDir = gameJar == null ? null : gameJar.toAbsolutePath().getParent();
		if (gameDir != null) {
			Path p = gameDir.resolve(side + "-" + minecraftVersion + "-mappings.txt");
			if (Files.isRegularFile(p)) return p;
			tried.add(p.toString());
		}
		String mcp = FMLLoader.versionInfo().mcpVersion();
		List<String> versions = new ArrayList<>(List.of(minecraftVersion));
		if (mcp != null && !mcp.isBlank()) versions.add(minecraftVersion + "-" + mcp);
		for (Path libraries : librariesDirectories()) {
			for (String version : versions) {
				Path p = libraries.resolve("net").resolve("minecraft").resolve(side).resolve(version).resolve(side + "-" + version + "-mappings.txt");
				if (Files.isRegularFile(p)) return p;
				tried.add(p.toString());
			}
		}
		Path tsrg = gameDir == null ? null : gameDir.resolve(side + "-" + minecraftVersion + "-mappings.tsrg");
		String unreadable = tsrg != null && Files.isRegularFile(tsrg) ? "; Forge's installer left " + tsrg + ", which this Fabricated can't read yet" : "";
		throw new MappingException("Mojang's " + side + " mapping file for " + minecraftVersion + " was not found (looked at " + String.join(", ", tried) + ")" + unreadable);
	}

	/** The libraries directories to look in: the launcher's, as a system property, and the one Forge's loader is in. */
	private static Set<Path> librariesDirectories() {
		Set<Path> directories = new LinkedHashSet<>();
		String property = System.getProperty("libraryDirectory");
		if (property != null && !property.isBlank()) {
			try {
				directories.add(Path.of(property).toAbsolutePath().normalize());
			} catch (InvalidPathException e) {
				LOGGER.debug("libraryDirectory {} is not a path", property);
			}
		}
		// <libraries>/net/minecraftforge/fmlloader/<version>/fmlloader-<version>.jar
		try {
			CodeSource source = FMLLoader.class.getProtectionDomain().getCodeSource();
			if (source != null && source.getLocation() != null && "file".equals(source.getLocation().getProtocol())) {
				Path jar = Path.of(source.getLocation().toURI());
				Path root = jar.getParent();
				for (int i = 0; i < 4 && root != null; i++) root = root.getParent();
				if (root != null && Files.isDirectory(root.resolve("net").resolve("minecraft"))) directories.add(root.normalize());
			}
		} catch (URISyntaxException | RuntimeException e) {
			LOGGER.debug("Could not tell the libraries directory from Forge's loader jar", e);
		}
		return directories;
	}
}
