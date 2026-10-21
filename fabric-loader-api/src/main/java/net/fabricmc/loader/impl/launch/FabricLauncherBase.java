package net.fabricmc.loader.impl.launch;

import java.nio.file.Path;
import java.util.List;

import io.github.cmdralph.fabricated.fabricloader.FabricLoaderImpl;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/** Stand-in for Fabric Loader's internal launcher holder, which some mods query for the environment. */
public abstract class FabricLauncherBase implements FabricLauncher {
	private static final FabricLauncher LAUNCHER = new FabricLauncherBase() {
	};

	protected FabricLauncherBase() {
	}

	public static FabricLauncher getLauncher() {
		return LAUNCHER;
	}

	@Override
	public EnvType getEnvironmentType() {
		return FabricLoader.getInstance().getEnvironmentType();
	}

	@Override
	public boolean isDevelopment() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	@Override
	public String getTargetNamespace() {
		return FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace();
	}

	@Override
	public ClassLoader getTargetClassLoader() {
		return FabricLoaderImpl.gameClassLoader();
	}

	@Override
	public List<Path> getClassPath() {
		return List.of();
	}

	@Override
	public void addToClassPath(Path path, String... allowedPrefixes) {
		FabricLoaderImpl.addToClassPath(path);
	}
}
