package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cpw.mods.modlauncher.api.NamedPath;
import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.core.resolve.MultiLoaderJars;
import io.github.cmdralph.fabricated.core.resolve.NestedNeoForgeJars;
import io.github.cmdralph.fabricated.core.translate.ModsTomlWriter;
import net.minecraftforge.fml.loading.ModDirTransformerDiscoverer;

/**
 * The Fabric jars in the mods folder (docs/PORTING.md §4.1). Forge's mods-folder locator loads a jar with
 * {@code META-INF/mods.toml} or an {@code FMLModType} manifest attribute and skips any other, so the jars with a
 * {@code fabric.mod.json} and neither are Fabricated's; a jar built for both Forge and Fabric loads as Forge's. A Fabric
 * jar whose {@code mods.toml} is only a placeholder for Sinytra Connector is Fabricated's too, kept from Forge's scan
 * ({@link #claimPlaceholders}, MULTI-2).
 */
final class FabricJars {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** The Fabric jars with Connector's placeholder for Forge's metadata that Forge's mods-folder scan leaves out. */
	private static final Set<Path> CLAIMED = ConcurrentHashMap.newKeySet();

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

	/**
	 * Whether Forge's view of a translated Fabric jar leaves this file out (a path in the jar). Forge's jar-in-jar locator
	 * reads {@code META-INF/jarjar/metadata.json} in every mod file and adds the jars it lists as modules of their own. A
	 * jar built for Fabric and NeoForge at once has that list for NeoForge (LambDynamicLights nests its NeoForge build
	 * and, through it, the Fabric libraries that {@code fabric.mod.json} nests too): Fabricated has translated those
	 * libraries already, and Forge added them again untranslated, two modules with the same packages (JIJ-1).
	 */
	static boolean hiddenFromForge(String path) {
		return path.equals(NestedNeoForgeJars.METADATA);
	}

	/**
	 * Cheap check of the central directory and manifest; full validation happens in the pipeline. A jar with Forge's
	 * metadata is Fabricated's only when Forge's mods-folder scan leaves it out for its placeholder ({@link #claimPlaceholders}).
	 */
	static boolean isFabricOnly(Path jar) {
		return switch (kind(jar)) {
			case FABRIC -> true;
			case WITH_FORGE_METADATA -> CLAIMED.contains(jar);
			case OTHER -> false;
		};
	}

	private enum Kind { FABRIC, WITH_FORGE_METADATA, OTHER }

	private static Kind kind(Path jar) {
		try (JarFile file = new JarFile(jar.toFile(), false)) {
			if (file.getEntry("fabric.mod.json") == null) return Kind.OTHER;
			Manifest manifest = file.getManifest();
			if (manifest != null && manifest.getMainAttributes().getValue("FMLModType") != null) return Kind.OTHER;
			return file.getEntry(ModsTomlWriter.FORGE_PATH) != null ? Kind.WITH_FORGE_METADATA : Kind.FABRIC;
		} catch (IOException | RuntimeException e) {
			// Not a zip or unreadable: Forge skips it too.
			return Kind.OTHER;
		}
	}

	/**
	 * Keeps Forge's mods-folder locator from the Fabric jars in {@code modsDir} whose {@code META-INF/mods.toml} is a
	 * placeholder for Sinytra Connector ({@link MultiLoaderJars#forgeFabricReason}, MULTI-2): Forge would load such a jar as
	 * a Forge mod, whose dependency on Connector's {@code connectormod} stops the launch (Origins 1.10.2's Fabric jar on
	 * Forge 47), where Fabricated loads it as the Fabric mod it is, as on NeoForge (MULTI-1). Fabricated's mod locator calls
	 * it, which Forge runs before its mods-folder locator (the order the logs of Forge 47's and 61's discovery list).
	 */
	static void claimPlaceholders(Path modsDir) {
		if (!Files.isDirectory(modsDir)) return;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
			for (Path jar : stream) {
				if (!Files.isRegularFile(jar) || kind(jar) != Kind.WITH_FORGE_METADATA) continue;
				String reason = MultiLoaderJars.forgeFabricReason(jar, ArchiveLimits.DEFAULTS);
				if (reason == null) continue;
				if (leaveOutOfModsFolder(jar)) {
					CLAIMED.add(jar);
					LOGGER.info("Fabricated loads {} as a Fabric mod: {}", jar.getFileName(), reason);
				} else {
					LOGGER.warn("Fabricated can't keep Forge from loading {} as a Forge mod, though {}; Forge reports what it lacks",
							jar.getFileName(), reason);
				}
			}
		} catch (IOException | RuntimeException e) {
			// The locator's only error path stops the game: such jars stay Forge's.
			LOGGER.warn("Fabricated could not list {}: {}", modsDir, e.toString());
		}
	}

	/**
	 * Adds a jar to the one list of jars Forge's mods-folder locator leaves out, whether it was left out: the jars
	 * {@code ModDirTransformerDiscoverer} found providing ModLauncher's or FML's services, which ModLauncher has loaded
	 * already. The list is a private field, read only by that locator ({@code allExcluded()}, in Forge 47 to 66's
	 * {@code ModsFolderLocator}, each read in its fmlloader); fmlloader is an automatic module, whose packages are open.
	 */
	private static boolean leaveOutOfModsFolder(Path jar) {
		try {
			Field found = ModDirTransformerDiscoverer.class.getDeclaredField("found");
			found.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<NamedPath> excluded = (List<NamedPath>) found.get(null);
			excluded.add(new NamedPath(ModIds.FABRICATED, jar));
			return ModDirTransformerDiscoverer.allExcluded().contains(jar);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.debug("Could not add {} to the jars Forge's mods-folder locator leaves out", jar, e);
			return false;
		}
	}
}
