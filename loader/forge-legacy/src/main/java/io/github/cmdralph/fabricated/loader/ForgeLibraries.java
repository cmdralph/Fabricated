package io.github.cmdralph.fabricated.loader;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Where Forge 47's installer leaves the game and its mappings, as its install profile names them (Forge's 1.20.1
 * {@code build.gradle}; which launchers keep them is UNVERIFIED, docs/PORTING.md §3.1): in
 * {@code <libraries>/net/minecraft/<side>/<mc>-<mcp>/}, {@code <side>-<mc>-<mcp>-mappings.txt} is Mojang's mappings
 * (ProGuard) and {@code -srg.jar} vanilla's classes in the names Forge runs them with (Mojang's classes, SRG members); in
 * {@code <libraries>/de/oceanlabs/mcp/mcp_config/<mc>-<mcp>/}, {@code mcp_config-<mc>-<mcp>-mappings.txt} is MCPConfig's
 * obfuscated → SRG mappings (TSRG v2). The {@code minecraft} mod file is that {@code -srg.jar}, in a union with the
 * installer's {@code -extra.jar} beside it, which holds vanilla's resources (assets and data) and no class (the logs of
 * forge-legacy's server smoke on #125).
 */
final class ForgeLibraries {
	private ForgeLibraries() {
	}

	/** The libraries directories to look in: the launcher's ({@code libraryDirectory}), then the nearest one holding {@code gameFile}. */
	static Set<Path> directories(Path gameFile) {
		Set<Path> directories = new LinkedHashSet<>();
		String property = System.getProperty("libraryDirectory");
		if (property != null && !property.isBlank()) {
			try {
				directories.add(Path.of(property).toAbsolutePath().normalize());
			} catch (InvalidPathException e) {
				// Not a path: the game file's directory still says.
			}
		}
		for (Path dir = gameFile == null ? null : gameFile.toAbsolutePath().normalize().getParent(); dir != null; dir = dir.getParent()) {
			if (Files.isDirectory(dir.resolve("net").resolve("minecraft"))) {
				directories.add(dir);
				break;
			}
		}
		return directories;
	}

	/** {@code <libraries>/net/minecraft/<side>/<version>/<side>-<version>-<suffix>}. */
	static Path gameFile(Path libraries, boolean client, String version, String suffix) {
		String side = client ? "client" : "server";
		return libraries.resolve("net").resolve("minecraft").resolve(side).resolve(version).resolve(side + "-" + version + "-" + suffix);
	}

	/** {@code <libraries>/de/oceanlabs/mcp/mcp_config/<version>/mcp_config-<version>-<suffix>}. */
	static Path mcpConfigFile(Path libraries, String version, String suffix) {
		return libraries.resolve("de").resolve("oceanlabs").resolve("mcp").resolve("mcp_config").resolve(version).resolve("mcp_config-" + version + "-" + suffix);
	}

	/** Vanilla's classes in the names Forge runs them with, as the installer left them: {@code <side>-<mc>-<mcp>-srg.jar}. */
	static Optional<Path> vanillaInRuntimeNames(Path gameFile, boolean client, String minecraftAndMcpVersion) {
		for (Path libraries : directories(gameFile)) {
			Path srg = gameFile(libraries, client, minecraftAndMcpVersion, "srg.jar");
			if (Files.isRegularFile(srg)) return Optional.of(srg);
		}
		return Optional.empty();
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
}
