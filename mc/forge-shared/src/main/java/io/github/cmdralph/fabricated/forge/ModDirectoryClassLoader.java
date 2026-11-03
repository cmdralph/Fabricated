package io.github.cmdralph.fabricated.forge;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import io.github.cmdralph.fabricated.core.runtime.EntrypointRunner;
import io.github.cmdralph.fabricated.core.runtime.FabricLoaderRuntime;

/**
 * The context class loader Fabric entrypoints run with on Forge (SCAN-1, as on NeoForge): the game's, which it
 * delegates everything to, and which also finds the directories of Fabric mods' jars
 * ({@link FabricLoaderRuntime#fabricModDirectories}), as Fabric Loader's class loader does. Classpath scanners start
 * from a package's directory (Reflections, which YUNG's API uses to find what it registers), and Forge's class loaders
 * give it none it can open, so such a scan found nothing (the Forge 26.1.2 corpus: Reflections' "Could not open url
 * connection", then none of YUNG's structure types or processors registered). A Fabric mod's directories are answered
 * here, before the game's loader is asked, as {@code jar:file:} URLs into the jars Fabricated opened.
 */
public final class ModDirectoryClassLoader extends ClassLoader {
	static {
		registerAsParallelCapable();
	}

	private final FabricLoaderRuntime runtime;

	private ModDirectoryClassLoader(ClassLoader parent, FabricLoaderRuntime runtime) {
		super(parent);
		this.runtime = runtime;
	}

	/** The hook an {@link EntrypointRunner} runs entrypoints with: each runs with this loader as its context loader. */
	public static EntrypointRunner.ContextHook contextHook(FabricLoaderRuntime runtime) {
		return new EntrypointRunner.ContextHook() {
			private ClassLoader previous;
			private ModDirectoryClassLoader loader;

			@Override
			public void enter(String modId) {
				Thread thread = Thread.currentThread();
				previous = thread.getContextClassLoader();
				if (previous == null) return;
				if (loader == null || loader.getParent() != previous) loader = new ModDirectoryClassLoader(previous, runtime);
				thread.setContextClassLoader(loader);
			}

			@Override
			public void exit() {
				Thread.currentThread().setContextClassLoader(previous);
			}
		};
	}

	@Override
	public URL getResource(String name) {
		List<URL> found = directories(name);
		return found.isEmpty() ? super.getResource(name) : found.get(0);
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		List<URL> found = directories(name);
		return found.isEmpty() ? super.getResources(name) : Collections.enumeration(found);
	}

	private List<URL> directories(String name) {
		List<URL> urls = new ArrayList<>();
		for (Path dir : runtime.fabricModDirectories(name)) {
			try {
				urls.add(dir.toUri().toURL());
			} catch (MalformedURLException | IllegalArgumentException e) {
				// A directory with no URL form is left out.
			}
		}
		return urls;
	}
}
