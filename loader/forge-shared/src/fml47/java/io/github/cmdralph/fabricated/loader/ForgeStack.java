package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.cmdralph.fabricated.core.Pipeline;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;
import io.github.cmdralph.fabricated.core.remap.MappingComposer;
import io.github.cmdralph.fabricated.core.remap.MappingException;
import io.github.cmdralph.fabricated.core.remap.MappingTable;
import io.github.cmdralph.fabricated.core.translate.PlatformDescriptor;
import io.github.cmdralph.fabricated.core.translate.TranslationEnvironment.Namespace;

/**
 * What Forge 47 does below the game where Forge 61 to 66 do otherwise ({@code loader/forge-shared/src/modern}): its
 * securejarhandler 2.1.10, SpongePowered's Mixin 0.8.5, and the game in Mojang's class names and SRG's member names, as
 * its installer leaves it, its install profile naming the files (Forge's 1.20.1 {@code build.gradle}; which launchers
 * keep them is UNVERIFIED, docs/PORTING.md §3.1): in {@code <libraries>/net/minecraft/<side>/<mc>-<mcp>/},
 * {@code <side>-<mc>-<mcp>-mappings.txt} is Mojang's mappings (ProGuard) and {@code -srg.jar} vanilla's classes in the
 * names Forge runs them with; in {@code <libraries>/de/oceanlabs/mcp/mcp_config/<mc>-<mcp>/},
 * {@code mcp_config-<mc>-<mcp>-mappings.txt} is MCPConfig's obfuscated → SRG mappings (TSRG v2). The {@code minecraft}
 * mod file is that {@code -srg.jar}, in a union with the installer's {@code -extra.jar} beside it, which holds vanilla's
 * resources (assets and data) and no class (the logs of forge-legacy's server smoke on #125).
 */
final class ForgeStack {
	/**
	 * securejarhandler takes a mod file's manifest, which Mixin reads ({@code MixinConfigs}, Mixin's
	 * {@code SecureJarResource}), from its first path, and searches its last path first (#89's server smoke: the test
	 * mod's mixin config was never read with the overlay last): the overlay goes first.
	 */
	static final boolean OVERLAY_FIRST = true;
	/** Where MCPConfig's obfuscated → SRG mappings are, for a launcher that keeps them elsewhere. */
	static final String SRG_SYSTEM_PROPERTY = "fabricated.srgMappingsPath";

	private ForgeStack() {
	}

	/** Forge 47's ({@link ForgeDescriptor}). */
	static PlatformDescriptor descriptor() {
		return ForgeDescriptor.INSTANCE;
	}

	/**
	 * The files of the game a Fabric mod reads through the {@code minecraft} mod container: the {@code minecraft} mod
	 * file's jar and, when it is the installer's {@code -srg.jar}, the {@code -extra.jar} beside it, which holds vanilla's
	 * resources. Fabric API's resource loader reads vanilla's {@code en_us.json} there as the game boots; without it the
	 * server's language had no strings. Both are files, as a Fabric mod expects of the game's origin.
	 */
	static List<Path> gameFiles(Path gameFile) {
		Path name = gameFile.getFileName();
		String file = name == null ? "" : name.toString();
		if (file.endsWith("-srg.jar")) {
			Path extra = gameFile.resolveSibling(file.substring(0, file.length() - "srg.jar".length()) + "extra.jar");
			if (Files.isRegularFile(extra)) return List.of(gameFile, extra);
		}
		return List.of(gameFile);
	}

	/**
	 * Vanilla's classes in the names Forge runs them with, as the installer left them: {@code <side>-<mc>-<mcp>-srg.jar}.
	 * Without them pre-flight goes without (the obfuscated jar it could read is in neither Mojang's names nor SRG's).
	 */
	static Pipeline.VanillaGame vanillaGame(Path gameFile, String minecraftVersion, boolean client, Namespace namespace, String mojangMappingsPath,
			TranslationCache cache, List<SafeArchive> opened) {
		String version = IntermediaryMappings.minecraftAndMcpVersion(minecraftVersion);
		for (Path libraries : ForgeLibraries.directories(gameFile)) {
			Path srg = ForgeLibraries.gameFile(libraries, client, version, "srg.jar");
			if (Files.isRegularFile(srg)) return ForgeLibraries.installedGame(srg, "Mojang's class names and SRG's member names", opened);
		}
		return new Pipeline.VanillaGame(ClassBytesSource.EMPTY, "not found: Forge's installer leaves it as " + (client ? "client" : "server")
				+ "-" + version + "-srg.jar among the libraries");
	}

	/** The mapping files Intermediary is composed with: MCPConfig's obfuscated → SRG mappings, then Mojang's. */
	static List<Path> mappingFiles(String minecraftVersion, Path mojang, Path gameFile) throws MappingException {
		return List.of(locateSrgMappings(minecraftVersion, gameFile), mojang);
	}

	/** Intermediary composed with MCPConfig's and Mojang's mappings into Forge 47's names ({@link MappingComposer#composeSrg}). */
	static MappingTable compose(Reader intermediary, List<Path> files) throws MappingException, IOException {
		try (Reader srg = Files.newBufferedReader(files.get(0), StandardCharsets.UTF_8);
				Reader mojang = Files.newBufferedReader(files.get(1), StandardCharsets.UTF_8)) {
			return MappingComposer.composeSrg(intermediary, srg, mojang);
		}
	}

	/**
	 * MCPConfig's obfuscated → SRG mappings. Candidates, in order: the {@value #SRG_SYSTEM_PROPERTY} system property, then
	 * in each libraries directory {@code de/oceanlabs/mcp/mcp_config/<mc>-<mcp>/mcp_config-<mc>-<mcp>-mappings.txt}, which
	 * Forge's installer extracts from MCPConfig's archive.
	 */
	private static Path locateSrgMappings(String minecraftVersion, Path gameFile) throws MappingException {
		List<String> tried = new ArrayList<>();
		Path configured = IntermediaryMappings.configured(tried, System.getProperty(SRG_SYSTEM_PROPERTY));
		if (configured != null) return configured;
		String version = IntermediaryMappings.minecraftAndMcpVersion(minecraftVersion);
		for (Path libraries : ForgeLibraries.directories(gameFile)) {
			Path p = libraries.resolve("de").resolve("oceanlabs").resolve("mcp").resolve("mcp_config").resolve(version).resolve("mcp_config-" + version + "-mappings.txt");
			if (Files.isRegularFile(p)) return p;
			tried.add(p.toString());
		}
		throw new MappingException("MCPConfig's SRG mapping file for " + minecraftVersion + " was not found (looked at " + String.join(", ", tried) + ")");
	}

	/** Pre-flight names Fabricated's constructor point where Mixin 0.8.5 takes a constructor's points at returns only. */
	static void registerInjectionPoints() {
		ConstructorInjectionPoint.register();
	}
}
