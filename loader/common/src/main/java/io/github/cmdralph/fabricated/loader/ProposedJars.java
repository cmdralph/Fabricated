package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.resolve.CandidateScanner;
import io.github.cmdralph.fabricated.core.resolve.DownloadedMods;
import io.github.cmdralph.fabricated.fabricloader.FabricLoaderImpl;

/**
 * The jars Fabric mods add to the class path while running (Fabric Loader's {@code addToClassPath}, through which
 * LuckPerms adds the libraries it downloads, LOADER-2). FML's module class loader takes no new jars, but it hands each
 * class whose package no module has to a fallback loader, which FML lets be set ({@code ModuleClassLoader}, read in
 * FML's source): this loader goes in front of the original fallback, its parent, and takes the added jars. Their
 * classes load untransformed and see the libraries and the JDK, not the game's or the mods' classes, as a library
 * needs. The original fallback isn't always the game loader's parent: ModLauncher's is the loader of the launcher's
 * libraries, through which vanilla reaches packages their modules don't export (Gson's {@code com.google.gson.internal},
 * GSON-1), so the platform names it.
 * <p>A Fabric mod's jar isn't added (DOWNLOAD-1): loaded here, untransformed and outside the game's modules, its classes
 * couldn't see the game's, its mixins would never apply, and its entrypoints would never run. Essential's loader adds
 * Essential so in the launch that downloads it; Fabricated loads that jar from the next launch, and says so.
 */
final class ProposedJars extends URLClassLoader {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static boolean installed;
	private final Path gameDir;
	private final Consumer<String> warning;

	static {
		registerAsParallelCapable();
	}

	private ProposedJars(ClassLoader parent, Path gameDir, Consumer<String> warning) {
		super("fabricated-proposed-jars", new URL[0], parent);
		this.gameDir = gameDir;
		this.warning = warning;
	}

	/** Puts the loader in front of {@code game}'s fallback where the platform makes it {@code game}'s parent. */
	static void install(ClassLoader game, Path gameDir, Consumer<String> warning) {
		install(game, game.getParent(), gameDir, warning);
	}

	/**
	 * Puts the loader in front of {@code fallback}, once; {@code game} is the class loader of the mods' modules, and
	 * {@code fallback} the loader the platform made its fallback.
	 *
	 * @param gameDir the game directory, where a loader's download is looked for ({@link DownloadedMods})
	 * @param warning shows the player a warning as the game loads (a Fabric mod's jar that loads from the next launch)
	 */
	static synchronized void install(ClassLoader game, ClassLoader fallback, Path gameDir, Consumer<String> warning) {
		if (installed) return;
		installed = true;
		try {
			Method setFallback = game.getClass().getMethod("setFallbackClassLoader", ClassLoader.class);
			ProposedJars jars = new ProposedJars(fallback, gameDir, warning);
			setFallback.invoke(game, jars);
			FabricLoaderImpl.classPathAppender(jars::add);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Fabric mods can't add jars to the class path: the game's class loader ({}) takes no fallback loader", game.getClass().getName(), e);
		}
	}

	private void add(Path path) {
		if (isFabricMod(path)) {
			String text = DownloadedMods.isLookedFor(path, gameDir)
					? "A Fabric mod's loader downloaded " + path.getFileName() + " and added it as the game started, when no mod can join"
							+ " the game's mods any more. Fabricated loads it from the next launch: restart the game to use it."
					: "A Fabric mod added " + path.getFileName() + ", a Fabric mod itself, as the game ran, when no mod can join the"
							+ " game's mods any more; Fabricated doesn't load it.";
			LOGGER.warn("{} ({})", text, path);
			try {
				warning.accept(text);
			} catch (RuntimeException e) {
				LOGGER.warn("Could not show the warning on the loading screen", e);
			}
			return;
		}
		try {
			addURL(path.toUri().toURL());
			LOGGER.info("A Fabric mod added {} to the class path", path.getFileName());
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("Not a file: " + path, e);
		}
	}

	/** Whether {@code path} is a Fabric mod's jar (a zip with {@code fabric.mod.json} at its root), not a library's. */
	static boolean isFabricMod(Path path) {
		try (ZipFile zip = new ZipFile(path.toFile())) {
			return zip.getEntry(CandidateScanner.FABRIC_MOD_JSON) != null;
		} catch (IOException | RuntimeException e) {
			// Not a zip the JDK reads: a class path entry it can't use either way, left as it was.
			return false;
		}
	}
}
