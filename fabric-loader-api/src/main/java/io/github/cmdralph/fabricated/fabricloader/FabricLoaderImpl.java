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

	private static volatile Function<ModContainer, ClassLoader> classLoaderLookup = mod -> {
		ClassLoader context = Thread.currentThread().getContextClassLoader();
		return context != null ? context : FabricLoaderImpl.class.getClassLoader();
	};

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

	/** Class loader that can see the classes of {@code mod}. */
	public static ClassLoader classLoaderFor(ModContainer mod) {
		return classLoaderLookup.apply(mod);
	}
}
