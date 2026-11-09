package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.cmdralph.fabricated.core.Pipeline;
import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;

/**
 * Where Forge's installer leaves the game: among the libraries, in {@code <libraries>/net/minecraft/<side>/<version>/},
 * the files {@link ForgeStack} names, which differ between Forge 47 and Forge 61 to 66.
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

	/** Vanilla's classes from a game jar Forge's installer left, in the names Forge runs the game with, its archive added to {@code opened}. */
	static Pipeline.VanillaGame installedGame(Path jar, String names, List<SafeArchive> opened) {
		try {
			SafeArchive archive = SafeArchive.open(jar, ArchiveLimits.DEFAULTS);
			opened.add(archive);
			if (!archive.contains("net/minecraft/server/MinecraftServer.class")) return new Pipeline.VanillaGame(ClassBytesSource.EMPTY, "not found: " + jar + " isn't a game jar");
			return new Pipeline.VanillaGame(name -> archive.read(name + ".class"), jar + " (Forge's installed game, in " + names + ")");
		} catch (IOException e) {
			return new Pipeline.VanillaGame(ClassBytesSource.EMPTY, "not found: " + jar + " can't be read: " + e.getMessage());
		}
	}
}
