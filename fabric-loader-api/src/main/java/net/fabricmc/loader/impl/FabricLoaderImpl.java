package net.fabricmc.loader.impl;

import java.io.File;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.ObjectShare;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.impl.entrypoint.EntrypointStorage;

/**
 * Stand-in for Fabric Loader's internal {@code FabricLoaderImpl}, which some mods use instead of the public API. Every
 * public API method works (it delegates to {@link FabricLoader#getInstance()}), and so do the private fields mods read
 * by reflection to add entrypoints of their own (LOADER-4); Fabric Loader's internal methods (game provider, mod
 * candidates) do not exist under Fabricated.
 */
public final class FabricLoaderImpl implements FabricLoader {
	public static final FabricLoaderImpl INSTANCE = new FabricLoaderImpl();

	/** Fabric Loader's entrypoint storage, which mods add to (FrozenLib); what they add goes to Fabricated's loader. */
	@SuppressWarnings("unused") // read by mods, by reflection
	private final EntrypointStorage entrypointStorage = new EntrypointStorage();
	/** Fabric Loader's language adapters by id, which mods pass back to the storage with what they add; read-only. */
	@SuppressWarnings("unused") // read by mods, by reflection
	private final Map<String, LanguageAdapter> adapterMap = new LanguageAdapters();

	private FabricLoaderImpl() {
	}

	/** The installed loader's language adapters, looked up on each use: the field is set when this class loads. */
	private static final class LanguageAdapters extends AbstractMap<String, LanguageAdapter> {
		private static Map<String, LanguageAdapter> adapters() {
			return io.github.cmdralph.fabricated.fabricloader.FabricLoaderImpl.internals().languageAdapters();
		}

		@Override
		public Set<Entry<String, LanguageAdapter>> entrySet() {
			return adapters().entrySet();
		}

		@Override
		public boolean containsKey(Object key) {
			return adapters().containsKey(key);
		}

		@Override
		public LanguageAdapter get(Object key) {
			return adapters().get(key);
		}
	}

	private static FabricLoader loader() {
		return FabricLoader.getInstance();
	}

	@Override
	public <T> List<T> getEntrypoints(String key, Class<T> type) {
		return loader().getEntrypoints(key, type);
	}

	@Override
	public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
		return loader().getEntrypointContainers(key, type);
	}

	@Override
	public <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker) {
		loader().invokeEntrypoints(key, type, invoker);
	}

	@Override
	public ObjectShare getObjectShare() {
		return loader().getObjectShare();
	}

	@Override
	public MappingResolver getMappingResolver() {
		return loader().getMappingResolver();
	}

	@Override
	public Optional<ModContainer> getModContainer(String id) {
		return loader().getModContainer(id);
	}

	@Override
	public Collection<ModContainer> getAllMods() {
		return loader().getAllMods();
	}

	@Override
	public boolean isModLoaded(String id) {
		return loader().isModLoaded(id);
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return loader().isDevelopmentEnvironment();
	}

	@Override
	public EnvType getEnvironmentType() {
		return loader().getEnvironmentType();
	}

	@Override
	public String getRawGameVersion() {
		return loader().getRawGameVersion();
	}

	@Override
	public Object getGameInstance() {
		return loader().getGameInstance();
	}

	@Override
	public Path getGameDir() {
		return loader().getGameDir();
	}

	@Override
	@Deprecated
	public File getGameDirectory() {
		return loader().getGameDir().toFile();
	}

	@Override
	public Path getConfigDir() {
		return loader().getConfigDir();
	}

	@Override
	@Deprecated
	public File getConfigDirectory() {
		return loader().getConfigDir().toFile();
	}

	@Override
	public String[] getLaunchArguments(boolean sanitize) {
		return loader().getLaunchArguments(sanitize);
	}
}
