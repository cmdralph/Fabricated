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

	/**
	 * Adds a jar's classes to those the game's class loader finds (in packages no mod has); Fabricated doesn't limit
	 * them to {@code allowedPrefixes}.
	 */
	void addToClassPath(Path path, String... allowedPrefixes);

	/**
	 * Loads a class with the game's class loader, without initializing it, as Fabric Loader's Knot does (OneConfig loads
	 * the entrypoint of a jar it added to the class path so).
	 */
	Class<?> loadIntoTarget(String name) throws ClassNotFoundException;
}
