package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import io.github.cmdralph.fabricated.core.archive.ArchiveLimits;
import io.github.cmdralph.fabricated.core.resolve.ConflictingJars;
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
 * MultiLoaderJars}). With Sinytra Connector or a second Fabricated jar there ({@link ConflictingJars}), it claims
 * nothing, and Fabricated stands down with an error naming them.
 */
public final class FabricCandidateLocator implements IModFileCandidateLocator {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** Read of a NeoForge jar's metadata, at most; Connector's is a few hundred bytes. */
	private static final int MAX_MODS_TOML = 256 * 1024;
	private static final List<Path> CLAIMED = new ArrayList<>();
	private static volatile String refusal;

	private enum Kind { FABRIC, FABRICATED, CONNECTOR, OTHER }

	@Override
	public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
		Path modsDir = FMLPaths.MODSDIR.get();
		if (!Files.isDirectory(modsDir)) return;
		List<Path> found = new ArrayList<>();
		List<Path> fabricated = new ArrayList<>();
		List<Path> connector = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
			for (Path jar : stream) {
				if (!Files.isRegularFile(jar)) continue;
				// Fabricated's and Connector's jars may be located already, as the platform's services.
				switch (kind(jar)) {
					case FABRIC -> {
						if (!context.isLocated(jar)) found.add(jar);
					}
					case FABRICATED -> fabricated.add(jar);
					case CONNECTOR -> connector.add(jar);
					case OTHER -> {
					}
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Fabricated could not list {}: {}", modsDir, e.toString());
		}
		refusal = ConflictingJars.refusal(fabricated, connector);
		if (refusal != null) {
			LOGGER.error("Fabricated: {}", refusal);
			found.clear();
			// The Fabricated jars the platform hasn't loaded as its services are claimed too, and so never loaded: NeoForge
			// 21.1 loads one as its service and its mods-folder scan the others as libraries, and a second copy of
			// Fabricated's module stops the launch with a module error before the refusal shows (DUP-2).
			for (Path jar : fabricated) {
				if (!context.isLocated(jar)) context.addLocated(jar);
			}
		}
		found.sort(null);
		for (Path jar : found) context.addLocated(jar);
		synchronized (CLAIMED) {
			CLAIMED.clear();
			CLAIMED.addAll(found);
		}
		LOGGER.info("Fabricated found {} Fabric mod jar(s) in {}", found.size(), modsDir);
	}

	/**
	 * Cheap checks of the central directory, unless the jar has both loaders' metadata or NeoForge's might be Connector's;
	 * full validation happens in the pipeline.
	 */
	private static Kind kind(Path jar) {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			if (zip.getEntry(ConflictingJars.FABRICATED_MARKER) != null) return Kind.FABRICATED;
			ZipEntry modsToml = zip.getEntry(MultiLoaderJars.MODS_TOML);
			if (zip.getEntry("fabric.mod.json") == null) return modsToml != null && isConnector(zip, modsToml) ? Kind.CONNECTOR : Kind.OTHER;
			if (modsToml == null) return Kind.FABRIC;
		} catch (IOException | RuntimeException e) {
			// Not a zip or unreadable: leave it to NeoForge, which reports broken files itself.
			return Kind.OTHER;
		}
		String reason = MultiLoaderJars.fabricReason(jar, ArchiveLimits.DEFAULTS);
		if (reason == null) return Kind.OTHER;
		LOGGER.info("Fabricated loads {} as a Fabric mod: {}", jar.getFileName(), reason);
		return Kind.FABRIC;
	}

	private static boolean isConnector(ZipFile zip, ZipEntry modsToml) throws IOException {
		ZipEntry manifest = zip.getEntry(JarFile.MANIFEST_NAME);
		if (manifest != null) {
			try (InputStream in = zip.getInputStream(manifest)) {
				if (ConflictingJars.CONNECTOR_MODULE.equals(new Manifest(in).getMainAttributes().getValue("Automatic-Module-Name"))) return true;
			}
		}
		try (InputStream in = zip.getInputStream(modsToml)) {
			return ConflictingJars.declaresConnector(new String(in.readNBytes(MAX_MODS_TOML), StandardCharsets.UTF_8));
		}
	}

	/** Why Fabricated stands down this launch (Sinytra Connector, or another Fabricated jar), or null. */
	static String refusal() {
		return refusal;
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
