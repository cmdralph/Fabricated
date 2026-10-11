package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

/**
 * Claims Fabric jars in the mods folder before NeoForge's own mods-folder scan, which would otherwise
 * reject them as incompatible. Claimed jars are processed later, all together, by
 * {@link FabricDependencyLocator}. Jars that also contain {@code META-INF/neoforge.mods.toml} are
 * multi-loader jars and are left to NeoForge.
 */
public final class FabricCandidateLocator implements IModFileCandidateLocator {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final List<Path> CLAIMED = new ArrayList<>();

	@Override
	public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
		Path modsDir = FMLPaths.MODSDIR.get();
		if (!Files.isDirectory(modsDir)) return;
		List<Path> found = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
			for (Path jar : stream) {
				if (!Files.isRegularFile(jar) || context.isLocated(jar)) continue;
				if (isFabricOnlyJar(jar)) {
					context.addLocated(jar);
					found.add(jar);
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Fabricated could not list {}: {}", modsDir, e.toString());
		}
		found.sort(null);
		synchronized (CLAIMED) {
			CLAIMED.clear();
			CLAIMED.addAll(found);
		}
		LOGGER.info("Fabricated found {} Fabric mod jar(s) in {}", found.size(), modsDir);
	}

	/** Cheap check of the central directory only; full validation happens in the pipeline. */
	static boolean isFabricOnlyJar(Path jar) {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			return zip.getEntry("fabric.mod.json") != null && zip.getEntry("META-INF/neoforge.mods.toml") == null;
		} catch (IOException | RuntimeException e) {
			// Not a zip or unreadable: leave it to NeoForge, which reports broken files itself.
			return false;
		}
	}

	/** Jars claimed during this launch, sorted by path. */
	static List<Path> claimed() {
		synchronized (CLAIMED) {
			return List.copyOf(CLAIMED);
		}
	}

	@Override
	public int getPriority() {
		// Run before NeoForge's built-in locators so the claimed jars are skipped by them.
		return HIGHEST_SYSTEM_PRIORITY;
	}

	@Override
	public String toString() {
		return "fabricated";
	}
}
