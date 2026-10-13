package net.fabricmc.loader.impl;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.ObjectShare;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

/**
 * Stand-in for Fabric Loader's internal {@code FabricLoaderImpl}, which some mods use instead of the public API. Every
 * public API method works (it delegates to {@link FabricLoader#getInstance()}); Fabric Loader's internal methods (game
 * provider, mod candidates) do not exist under Fabricated.
 */
public final class FabricLoaderImpl implements FabricLoader {
	public static final FabricLoaderImpl INSTANCE = new FabricLoaderImpl();

	private FabricLoaderImpl() {
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
