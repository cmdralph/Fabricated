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

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.resolve.MultiLoaderJars;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

/**
 * Claims Fabric jars in the mods folder before NeoForge's own mods-folder scan, which would otherwise
 * reject them as incompatible. Claimed jars are processed later, all together, by
 * {@link FabricDependencyLocator}. Jars that also contain {@code META-INF/neoforge.mods.toml} are
 * multi-loader jars and are left to NeoForge, unless their NeoForge side starts nothing of its own ({@link
 * MultiLoaderJars}).
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

	/** Cheap check of the central directory, unless the jar has both loaders' metadata; full validation happens in the pipeline. */
	static boolean isFabricOnlyJar(Path jar) {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			if (zip.getEntry("fabric.mod.json") == null) return false;
			if (zip.getEntry(MultiLoaderJars.MODS_TOML) == null) return true;
		} catch (IOException | RuntimeException e) {
			// Not a zip or unreadable: leave it to NeoForge, which reports broken files itself.
			return false;
		}
		if (MultiLoaderJars.forNeoForge(jar, ArchiveLimits.DEFAULTS)) return false;
		LOGGER.info("Fabricated loads {} as a Fabric mod: it declares a NeoForge mod too, but no class of it for NeoForge to start (MULTI-1)",
				jar.getFileName());
		return true;
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
