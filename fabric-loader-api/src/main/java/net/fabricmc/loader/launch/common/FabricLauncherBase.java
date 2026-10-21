package net.fabricmc.loader.launch.common;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import net.fabricmc.api.EnvType;

/**
 * Fabric Loader's legacy launcher holder, which older mods still use: LuckPerms adds the libraries it downloads to the
 * class path with {@code getLauncher().propose(url)} (LOADER-2). Delegates to the current launcher, as upstream's does.
 */
@Deprecated
public class FabricLauncherBase implements FabricLauncher {
	private final net.fabricmc.loader.impl.launch.FabricLauncher parent = net.fabricmc.loader.impl.launch.FabricLauncherBase.getLauncher();

	public static Class<?> getClass(String className) throws ClassNotFoundException {
		return Class.forName(className, true, getLauncher().getTargetClassLoader());
	}

	public static FabricLauncher getLauncher() {
		return new FabricLauncherBase();
	}

	@Override
	public void propose(URL url) {
		try {
			parent.addToClassPath(Path.of(url.toURI()));
		} catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException e) {
			throw new IllegalArgumentException("Not a file: " + url, e);
		}
	}

	@Override
	public EnvType getEnvironmentType() {
		return parent.getEnvironmentType();
	}

	/** Fabricated can't tell which classes the game's class loader has loaded. */
	@Override
	public boolean isClassLoaded(String name) {
		throw new UnsupportedOperationException("Fabricated can't tell whether " + name + " is loaded");
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		return getTargetClassLoader().getResourceAsStream(name);
	}

	@Override
	public ClassLoader getTargetClassLoader() {
		return parent.getTargetClassLoader();
	}

	/** The class file as the jar has it: Fabricated can't give it as NeoForge's transformers would make it. */
	@Override
	public byte[] getClassByteArray(String name, boolean runTransformers) throws IOException {
		try (InputStream in = getResourceAsStream(name.replace('.', '/') + ".class")) {
			return in == null ? null : in.readAllBytes();
		}
	}

	@Override
	public boolean isDevelopment() {
		return parent.isDevelopment();
	}

	/** Fabricated loads mods through NeoForge, not a class path; this is empty. */
	@Override
	public Collection<URL> getLoadTimeDependencies() {
		return List.of();
	}
}
