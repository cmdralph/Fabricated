package io.github.cmdralph.fabricated.neoforge;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import io.github.cmdralph.fabricated.core.runtime.FabricLoaderRuntime;

/**
 * The context class loader Fabric entrypoints run with (SCAN-1): the game's, which it delegates everything to, and
 * which also finds the directories of Fabric mods' jars ({@link FabricLoaderRuntime#fabricModDirectories}), as Fabric
 * Loader's class loader does. Classpath scanners start from a package's directory (Reflections, which YUNG's API uses
 * to find what it registers); NeoForge's loader finds only files, so such a scan silently found nothing, and NeoForge
 * 21.1's finds directories as {@code union:} URLs that only it opens. So a Fabric mod's directories are answered here,
 * before the game's loader is asked, as {@code jar:file:} URLs, which nothing here opens.
 */
final class ModDirectoryClassLoader extends ClassLoader {
	static {
		registerAsParallelCapable();
	}

	private final FabricLoaderRuntime runtime;

	ModDirectoryClassLoader(ClassLoader parent, FabricLoaderRuntime runtime) {
		super(parent);
		this.runtime = runtime;
	}

	@Override
	public URL getResource(String name) {
		List<URL> found = directories(name);
		return found.isEmpty() ? super.getResource(name) : found.getFirst();
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
