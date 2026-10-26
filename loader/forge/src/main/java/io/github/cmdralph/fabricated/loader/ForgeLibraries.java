package io.github.cmdralph.fabricated.loader;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Where Forge's installer leaves the game (docs/PORTING.md §4.1, read in the forge branch's server smoke): in
 * {@code <libraries>/net/minecraft/<side>/<mc>/}, {@code <side>-<mc>-unpacked.jar} is vanilla as Mojang's bundler holds
 * it (obfuscated on 1.21.11, in Mojang's names on 26.x), and on 1.21.11 {@code -official.jar} is vanilla in Mojang's
 * names and {@code -mappings.tsrg} Mojang's mappings. Forge's own jar is among the same libraries, and is the path of
 * the {@code minecraft} mod file.
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

	/**
	 * Vanilla in Mojang's names, as the installer left it: {@code -official.jar}, and where the game isn't obfuscated
	 * (26.x) {@code -unpacked.jar}.
	 */
	static Optional<Path> vanillaInMojangNames(Path gameFile, boolean client, String version, boolean obfuscated) {
		for (Path libraries : directories(gameFile)) {
			Path official = gameFile(libraries, client, version, "official.jar");
			if (Files.isRegularFile(official)) return Optional.of(official);
			Path unpacked = gameFile(libraries, client, version, "unpacked.jar");
			if (!obfuscated && Files.isRegularFile(unpacked)) return Optional.of(unpacked);
		}
		return Optional.empty();
	}
}
