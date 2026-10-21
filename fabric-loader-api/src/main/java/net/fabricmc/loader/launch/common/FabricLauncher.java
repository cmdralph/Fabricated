package net.fabricmc.loader.launch.common;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;

import net.fabricmc.api.EnvType;

/** Fabric Loader's legacy launcher interface, which older mods still use; see {@link FabricLauncherBase}. */
@Deprecated
public interface FabricLauncher {
	/** Adds a jar's classes to those the game's class loader finds. */
	void propose(URL url);

	EnvType getEnvironmentType();

	boolean isClassLoaded(String name);

	InputStream getResourceAsStream(String name);

	ClassLoader getTargetClassLoader();

	byte[] getClassByteArray(String name, boolean runTransformers) throws IOException;

	boolean isDevelopment();

	Collection<URL> getLoadTimeDependencies();
}
