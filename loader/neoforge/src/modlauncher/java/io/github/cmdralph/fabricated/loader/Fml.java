package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.Manifest;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarContentsBuilder;
import cpw.mods.jarhandling.SecureJar;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

/**
 * What Fabricated reads of NeoForge's FML and its mod files, on NeoForge 21.1's FML 4, which runs on ModLauncher: its mod
 * files are secure jars, and it has no program arguments for a locator (ModLauncher hands them to the game's launch
 * handler only, as on Forge). FML 10 and later have their own ({@code loader/neoforge/src/fml}).
 */
final class Fml {
	private Fml() {
	}

	static String minecraftVersion() {
		return FMLLoader.versionInfo().mcVersion();
	}

	static String neoForgeVersion() {
		return FMLLoader.versionInfo().neoForgeVersion();
	}

	/** Minecraft's version with the NeoForm build the installer made the game from, as in its libraries' folder names. */
	static String minecraftAndNeoFormVersion() {
		return FMLLoader.versionInfo().mcAndNeoFormVersion();
	}

	static Path gameDir() {
		return FMLLoader.getGamePath();
	}

	static boolean client() {
		return FMLLoader.getDist().isClient();
	}

	static boolean production() {
		return FMLLoader.isProduction();
	}

	/** The game's program arguments: none, as a locator can't read them here. */
	static String[] programArgs() {
		return new String[0];
	}

	/** A file of a mod file, or null when it has none. */
	static byte[] read(IModFile file, String path) throws IOException {
		Optional<InputStream> stream = file.getSecureJar().moduleDataProvider().open(path);
		if (stream.isEmpty()) return null;
		try (InputStream in = stream.get()) {
			return in.readAllBytes();
		}
	}

	static boolean contains(IModFile file, String path) {
		return file.getSecureJar().moduleDataProvider().findFile(path).isPresent();
	}

	/** The packages of a mod file's module, '.'-separated. */
	static Set<String> packages(IModFile file) {
		return file.getSecureJar().moduleDataProvider().descriptor().packages();
	}

	static Manifest manifest(IModFile file) {
		return file.getSecureJar().moduleDataProvider().getManifest();
	}

	/** The name of the module NeoForge makes of a mod file. */
	static String moduleName(IModFile file) {
		return file.getSecureJar().name();
	}

	/**
	 * Hands NeoForge a mod file made of the jars, each with the files the mod's module has; empty if NeoForge refuses it.
	 * The union file system asks its filter about directories too (their paths end in '/'), which stay: a package's
	 * directory goes with its files.
	 */
	static Optional<IModFile> addModFile(IDiscoveryPipeline pipeline, List<FilteredPath> paths, ModFileDiscoveryAttributes attributes)
			throws IOException {
		Map<Path, Predicate<String>> filters = new HashMap<>();
		Path[] jars = new Path[paths.size()];
		for (int i = 0; i < jars.length; i++) {
			FilteredPath part = paths.get(i);
			jars[i] = part.path();
			if (part.keeps() != null) filters.put(part.path(), part.keeps());
		}
		JarContents contents = new JarContentsBuilder().paths(jars).pathFilter((entry, base) -> {
			Predicate<String> keeps = filters.get(base);
			return keeps == null || entry.endsWith("/") || keeps.test(entry);
		}).build();
		return pipeline.addJarContent(contents, attributes, IncompatibleFileReporting.ERROR);
	}
}
