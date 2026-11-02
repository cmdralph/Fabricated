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
 *
 * <p>A Forge jar with its own copy of Fabric Loader's API ({@link FabricLoaderCopies}: Forgified Fabric API for 1.20.1
 * nests one, as Connector does) is refused too, and at once (PROVIDER-2): Forge would go on to build the game's layer
 * with Forge's error screen, which reads both copies and fails with a module error naming neither mod, so the error
 * would never show. The locator's exception stops the launch before that, Fabricated's error in the log and the crash.
 */
final class ConflictingMods {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** Read of a Forge jar's metadata, at most; Connector's is a few hundred bytes. */
	private static final int MAX_MODS_TOML = 256 * 1024;
	private static String refusal;
	/** Whether the refusal must stop the launch at once: a jar in the mods folder has its own copy of Fabric Loader. */
	private static boolean atOnce;
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
		List<String> loaderCopies = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
			for (Path jar : stream) {
				if (!Files.isRegularFile(jar)) continue;
				try (ZipFile zip = new ZipFile(jar.toFile())) {
					if (zip.getEntry(ConflictingJars.FABRICATED_MARKER) != null) {
						fabricated.add(jar);
						continue;
					}
					if (isConnector(zip)) connector.add(jar);
					// A Fabric jar is Fabricated's to read, Forge leaving it alone; a Forge jar Forge reads as it is.
					String copy = FabricJars.isFabricOnly(jar) ? null : FabricLoaderCopies.copyIn(zip);
					if (copy != null) {
						String name = String.valueOf(jar.getFileName());
						loaderCopies.add(copy.isEmpty() ? name : name + " (its " + copy.substring(copy.lastIndexOf('/') + 1) + ")");
					}
				} catch (IOException | RuntimeException e) {
					// Not a zip or unreadable: Forge reports broken files itself.
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Fabricated could not list {}: {}", modsDir, e.toString());
		}
		refusal = ConflictingJars.refusal(fabricated, connector);
		if (refusal == null && !loaderCopies.isEmpty()) refusal = loaderCopyRefusal(loaderCopies);
		atOnce = refusal != null && !loaderCopies.isEmpty();
		if (refusal != null) LOGGER.error("Fabricated: {}", refusal);
		return refusal;
	}

	/** The refusal of jars with their own copy of Fabric Loader (PROVIDER-2). */
	static String loaderCopyRefusal(List<String> jars) {
		List<String> sorted = jars.stream().sorted().toList();
		return String.join(", ", sorted) + (sorted.size() == 1 ? " has its own copy" : " have their own copies")
				+ " of Fabric Loader, and one game can't load it beside Fabricated's: remove "
				+ (sorted.size() == 1 ? "that jar" : "those jars") + ", or Fabricated, from the mods folder."
				+ " Forgified Fabric API nests one; for Fabric mods, Fabricated runs Fabric's own Fabric API jar. The launch stops here.";
	}

	/**
	 * What the mod locator throws to refuse the launch. Forge's discovery error, which Forge's discovery collects, on
	 * Forge 47 to 66 alike: the launch stops with the text, on the client on Forge's error screen, on the server in its log
	 * and crash report. Forge reads the text as a message pattern, so its quotes and braces are escaped. With a copy of
	 * Fabric Loader in the mods folder, that screen's layer can't be built, so the launch stops at once instead
	 * ({@link LaunchRefused}).
	 */
	static synchronized RuntimeException stop(String refusal) {
		if (atOnce) return new LaunchRefused(refusal);
		String pattern = refusal.replace("'", "''").replace("{", "'{'").replace("}", "'}'");
		return new EarlyLoadingException(refusal, null, List.of(new EarlyLoadingException.ExceptionData(pattern)));
	}

	/**
	 * Stops the launch from the mod locator, before Forge reads the mods' nested jars or builds a layer: Forge's discovery
	 * collects only its own errors, so this one ends the launch with its text, in the log and the launcher's crash.
	 */
	static final class LaunchRefused extends RuntimeException {
		private static final long serialVersionUID = 1L;

		LaunchRefused(String refusal) {
			super("Fabricated: " + refusal, null, false, false);
		}
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
