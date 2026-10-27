package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.translate.ModsTomlWriter;

/**
 * The Fabric jars in the mods folder (docs/PORTING.md §4.1). Forge's mods-folder locator loads a jar with
 * {@code META-INF/mods.toml} or an {@code FMLModType} manifest attribute and skips any other, so the jars with a
 * {@code fabric.mod.json} and neither are Fabricated's; a jar built for both Forge and Fabric loads as Forge's, as
 * nothing keeps Forge from loading it.
 */
final class FabricJars {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	private FabricJars() {
	}

	/** The Fabric jars directly in {@code modsDir}, sorted by path. */
	static List<Path> in(Path modsDir) {
		if (!Files.isDirectory(modsDir)) return List.of();
		List<Path> found = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
			for (Path jar : stream) {
				if (Files.isRegularFile(jar) && isFabricOnly(jar)) found.add(jar);
			}
		} catch (IOException e) {
			LOGGER.warn("Fabricated could not list {}: {}", modsDir, e.toString());
		}
		found.sort(null);
		LOGGER.info("Fabricated found {} Fabric mod jar(s) in {}", found.size(), modsDir);
		return found;
	}

	/** Cheap check of the central directory and manifest; full validation happens in the pipeline. */
	static boolean isFabricOnly(Path jar) {
		try (JarFile file = new JarFile(jar.toFile(), false)) {
			if (file.getEntry("fabric.mod.json") == null || file.getEntry(ModsTomlWriter.FORGE_PATH) != null) return false;
			Manifest manifest = file.getManifest();
			return manifest == null || manifest.getMainAttributes().getValue("FMLModType") == null;
		} catch (IOException | RuntimeException e) {
			// Not a zip or unreadable: Forge skips it too.
			return false;
		}
	}
}
