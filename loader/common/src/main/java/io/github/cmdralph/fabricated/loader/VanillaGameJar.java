package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import io.github.cmdralph.fabricated.core.Pipeline;
import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.bytecode.ClassBytesSource;
import io.github.cmdralph.fabricated.core.cache.TranslationCache;

/**
 * Vanilla's game jar, which NeoForge's installer patched into the jar the game runs, found next to the libraries that
 * jar is among (VANILLA-1): a client's in the launcher's {@code versions} folder, or among the libraries as Prism
 * Launcher and MultiMC keep it; a server's among the libraries, as Mojang's bundler, whose nested game jar is extracted
 * into the translation cache once. Pre-flight only reads its classes.
 */
final class VanillaGameJar {
	private static final String BUNDLED_VERSIONS = "META-INF/versions.list";

	private VanillaGameJar() {
	}

	/**
	 * Vanilla's game for {@code gameJar} (the patched one), its archives added to {@code opened} for the caller to close;
	 * without classes, saying where it was looked for, when it isn't there or can't be read.
	 */
	static Pipeline.VanillaGame find(Path gameJar, String mcVersion, boolean client, TranslationCache cache, List<SafeArchive> opened) {
		Path libraries = gameJar.toAbsolutePath().getParent();
		while (libraries != null && (libraries.getFileName() == null || !libraries.getFileName().toString().equals("libraries"))) {
			libraries = libraries.getParent();
		}
		if (libraries == null) return missing("the game jar " + gameJar + " isn't in a libraries folder");
		List<Path> candidates = client
				? List.of(libraries.resolveSibling("versions").resolve(mcVersion).resolve(mcVersion + ".jar"),
						libraries.resolve("com/mojang/minecraft").resolve(mcVersion).resolve("minecraft-" + mcVersion + "-client.jar"))
				: List.of(libraries.resolve("net/minecraft/server").resolve(mcVersion).resolve("server-" + mcVersion + ".jar"));
		for (Path candidate : candidates) {
			if (!Files.isRegularFile(candidate)) continue;
			try {
				SafeArchive archive = SafeArchive.open(candidate, ArchiveLimits.DEFAULTS);
				opened.add(archive);
				String location = candidate.toString();
				String nested = bundledJar(archive);
				if (nested != null) {
					// Keyed by the bundler's size and time, which a new download changes, rather than by its hash (read each launch).
					String bundler = "vanilla-" + candidate.getFileName() + "-" + Files.size(candidate) + "-" + Files.getLastModifiedTime(candidate).toMillis();
					Path extracted = cache.nested(bundler, nested).orElse(null);
					if (extracted == null) {
						byte[] data = archive.read(nested);
						if (data == null) return missing(candidate + " lists " + nested + ", which it doesn't hold");
						extracted = cache.storeNested(data, bundler, nested);
					}
					archive = SafeArchive.open(extracted, ArchiveLimits.DEFAULTS);
					opened.add(archive);
					location += "!/" + nested;
				}
				if (!archive.contains("net/minecraft/server/MinecraftServer.class")) return missing(location + " isn't a game jar");
				SafeArchive classes = archive;
				return new Pipeline.VanillaGame(name -> classes.read(name + ".class"), location);
			} catch (IOException e) {
				return missing(candidate + " can't be read: " + e.getMessage());
			}
		}
		return missing("looked for " + candidates.stream().map(Path::toString).collect(Collectors.joining(", ")));
	}

	/** The game jar a server bundler holds (its first listed version), or null when the archive isn't a bundler. */
	private static String bundledJar(SafeArchive archive) throws IOException {
		String list = archive.readText(BUNDLED_VERSIONS);
		if (list == null) return null;
		// Each line: hash, version id, path under META-INF/versions/.
		String[] first = list.lines().findFirst().orElse("").split("\t");
		return first.length == 3 ? "META-INF/versions/" + first[2] : null;
	}

	private static Pipeline.VanillaGame missing(String why) {
		return new Pipeline.VanillaGame(ClassBytesSource.EMPTY, "not found: " + why);
	}
}
