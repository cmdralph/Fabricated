package net.fabricmc.loader.impl.launch;

import java.nio.file.Path;
import java.util.List;

import net.fabricmc.api.EnvType;

/** The parts of Fabric Loader's internal launcher interface that mods use; see {@link FabricLauncherBase}. */
public interface FabricLauncher {
	EnvType getEnvironmentType();

	boolean isDevelopment();

	/** The namespace the game runs in, as {@code MappingResolver.getCurrentRuntimeNamespace()}. */
	String getTargetNamespace();

	ClassLoader getTargetClassLoader();

	/** Fabricated loads mods through NeoForge, not a class path; this is empty. */
	List<Path> getClassPath();
}
