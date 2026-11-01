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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.resolve.ConflictingJars;
import io.github.cmdralph.fabricated.core.translate.ModsTomlWriter;
import net.minecraftforge.fml.loading.EarlyLoadingException;

/**
 * Sinytra Connector or a second Fabricated jar in the mods folder ({@link ConflictingJars}), on Forge 47 to 66 alike, as
 * NeoForge's locator checks: Fabricated then loads no Fabric mod, and its mod locator, which Forge runs before the
 * dependency locators, stops the launch with an error naming them. Connector is known by its mod id in
 * {@code META-INF/mods.toml} or its module name, a Fabricated jar by a class only Fabricated's jars have.
 */
final class ConflictingMods {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** Read of a Forge jar's metadata, at most; Connector's is a few hundred bytes. */
	private static final int MAX_MODS_TOML = 256 * 1024;
	private static String refusal;
	private static boolean scanned;

	private ConflictingMods() {
	}

	/** Why Fabricated stands down this launch, or null: the mods folder is read once, by whichever locator asks first. */
	static synchronized String refusal(Path modsDir) {
		if (scanned) return refusal;
		scanned = true;
		if (!Files.isDirectory(modsDir)) return null;
		List<Path> fabricated = new ArrayList<>();
		List<Path> connector = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
			for (Path jar : stream) {
				if (!Files.isRegularFile(jar)) continue;
				try (ZipFile zip = new ZipFile(jar.toFile())) {
					if (zip.getEntry(ConflictingJars.FABRICATED_MARKER) != null) {
						fabricated.add(jar);
					} else if (isConnector(zip)) {
						connector.add(jar);
					}
				} catch (IOException | RuntimeException e) {
					// Not a zip or unreadable: Forge reports broken files itself.
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Fabricated could not list {}: {}", modsDir, e.toString());
		}
		refusal = ConflictingJars.refusal(fabricated, connector);
		if (refusal != null) LOGGER.error("Fabricated: {}", refusal);
		return refusal;
	}

	/**
	 * Forge's discovery error, which a locator throws and Forge's discovery collects, on Forge 47 to 66 alike: the launch
	 * stops with the text, on the client on Forge's error screen, on the server in its log and crash report. Forge reads
	 * the text as a message pattern, so its quotes and braces are escaped.
	 */
	static EarlyLoadingException stop(String refusal) {
		String pattern = refusal.replace("'", "''").replace("{", "'{'").replace("}", "'}'");
		return new EarlyLoadingException(refusal, null, List.of(new EarlyLoadingException.ExceptionData(pattern)));
	}

	private static boolean isConnector(ZipFile zip) throws IOException {
		ZipEntry manifest = zip.getEntry(JarFile.MANIFEST_NAME);
		if (manifest != null) {
			try (InputStream in = zip.getInputStream(manifest)) {
				if (ConflictingJars.CONNECTOR_MODULE.equals(new Manifest(in).getMainAttributes().getValue("Automatic-Module-Name"))) return true;
			}
		}
		ZipEntry modsToml = zip.getEntry(ModsTomlWriter.FORGE_PATH);
		if (modsToml == null) return false;
		try (InputStream in = zip.getInputStream(modsToml)) {
			return ConflictingJars.declaresConnector(new String(in.readNBytes(MAX_MODS_TOML), StandardCharsets.UTF_8));
		}
	}
}
