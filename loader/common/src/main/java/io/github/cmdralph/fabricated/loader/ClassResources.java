package io.github.cmdralph.fabricated.loader;

import java.io.InputStream;
import java.net.URL;

/**
 * What Fabric mods' {@code Class.getResource} and {@code Class.getResourceAsStream} calls become (RES-1, see
 * {@code ResourceLookups}). Fabric runs mods in unnamed modules, where those look the name up on the whole class path;
 * NeoForge runs each mod in a module of its own, where they find only that module's files, so a mod reading another
 * jar's file through its own class got nothing (Lattice reads Minecraft's {@code /version.json} that way, and stopped
 * the game while Mixin prepared its config). The class's own module is asked first, as before; then, as on Fabric, its
 * class loader.
 */
public final class ClassResources {
	private ClassResources() {
	}

	public static InputStream getResourceAsStream(Class<?> type, String name) {
		InputStream own = type.getResourceAsStream(name);
		return own != null ? own : loader(type).getResourceAsStream(absolute(type, name));
	}

	public static URL getResource(Class<?> type, String name) {
		URL own = type.getResource(name);
		return own != null ? own : loader(type).getResource(absolute(type, name));
	}

	private static ClassLoader loader(Class<?> type) {
		ClassLoader loader = type.getClassLoader();
		return loader != null ? loader : ClassLoader.getSystemClassLoader();
	}

	/** The name as {@code Class} resolves it: without the leading slash, or else in the class's package. */
	private static String absolute(Class<?> type, String name) {
		if (name.startsWith("/")) return name.substring(1);
		String pkg = type.getPackageName();
		return pkg.isEmpty() ? name : pkg.replace('.', '/') + "/" + name;
	}
}
