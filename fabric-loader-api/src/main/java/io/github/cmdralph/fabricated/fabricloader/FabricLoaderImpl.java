package io.github.cmdralph.fabricated.fabricloader;

import java.util.Objects;
import java.util.function.Function;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Holder for the active {@link FabricLoader} implementation. {@link FabricLoader#getInstance()} reads
 * {@link #INSTANCE}; Fabricated installs its runtime during NeoForge mod discovery.
 */
public final class FabricLoaderImpl {
	/** The installed loader, or {@code null} before Fabricated has bootstrapped. */
	public static volatile FabricLoader INSTANCE;

	/** The class loader of the game and its mods (one for all of them on NeoForge), once known. */
	private static volatile ClassLoader gameClassLoader;
	private static volatile Function<ModContainer, ClassLoader> classLoaderLookup = mod -> gameClassLoader();

	private FabricLoaderImpl() {
	}

	/** Installs the runtime loader. May be called again only with the same instance. */
	public static synchronized void install(FabricLoader loader, Function<ModContainer, ClassLoader> classLoaders) {
		Objects.requireNonNull(loader, "loader");
		Objects.requireNonNull(classLoaders, "classLoaders");
		if (INSTANCE != null && INSTANCE != loader) {
			throw new IllegalStateException("A different FabricLoader implementation is already installed: " + INSTANCE);
		}
		INSTANCE = loader;
		classLoaderLookup = classLoaders;
	}

	/** Records the class loader of the game and its mods; the first one recorded stays. */
	public static void gameClassLoader(ClassLoader loader) {
		if (gameClassLoader == null) gameClassLoader = Objects.requireNonNull(loader, "loader");
	}

	/**
	 * The class loader of the game and its mods (Fabric's target class loader). Until it is known, the calling thread's
	 * context class loader.
	 */
	public static ClassLoader gameClassLoader() {
		ClassLoader loader = gameClassLoader;
		if (loader == null) loader = Thread.currentThread().getContextClassLoader();
		return loader != null ? loader : FabricLoaderImpl.class.getClassLoader();
	}

	/** Class loader that can see the classes of {@code mod}. */
	public static ClassLoader classLoaderFor(ModContainer mod) {
		return classLoaderLookup.apply(mod);
	}
}
