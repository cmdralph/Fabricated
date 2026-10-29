package io.github.cmdralph.fabricated.loader;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 */
final class ProposedJars extends URLClassLoader {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static boolean installed;

	static {
		registerAsParallelCapable();
	}

	private ProposedJars(ClassLoader parent) {
		super("fabricated-proposed-jars", new URL[0], parent);
	}

	/** Puts the loader in front of {@code game}'s fallback where the platform makes it {@code game}'s parent. */
	static void install(ClassLoader game) {
		install(game, game.getParent());
	}

	/**
	 * Puts the loader in front of {@code fallback}, once; {@code game} is the class loader of the mods' modules, and
	 * {@code fallback} the loader the platform made its fallback.
	 */
	static synchronized void install(ClassLoader game, ClassLoader fallback) {
		if (installed) return;
		installed = true;
		try {
			Method setFallback = game.getClass().getMethod("setFallbackClassLoader", ClassLoader.class);
			ProposedJars jars = new ProposedJars(fallback);
			setFallback.invoke(game, jars);
			FabricLoaderImpl.classPathAppender(jars::add);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Fabric mods can't add jars to the class path: the game's class loader ({}) takes no fallback loader", game.getClass().getName(), e);
		}
	}

	private void add(Path path) {
		try {
			addURL(path.toUri().toURL());
			LOGGER.info("A Fabric mod added {} to the class path", path.getFileName());
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("Not a file: " + path, e);
		}
	}
}
