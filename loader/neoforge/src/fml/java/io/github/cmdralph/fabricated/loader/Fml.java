package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Manifest;

import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.jarmoduleinfo.JarModuleInfo;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

/**
 * What Fabricated reads of NeoForge's FML and its mod files, on FML 10 and later (NeoForge 21.11 and 26.x), whose mod
 * files have FML's own jar contents. NeoForge 21.1's FML 4 has its own ({@code loader/neoforge/src/modlauncher}).
 */
final class Fml {
	private Fml() {
	}

	static String minecraftVersion() {
		return FMLLoader.getCurrent().getVersionInfo().mcVersion();
	}

	static String neoForgeVersion() {
		return FMLLoader.getCurrent().getVersionInfo().neoForgeVersion();
	}

	/** Minecraft's version with the NeoForm build the installer made the game from, as in its libraries' folder names. */
	static String minecraftAndNeoFormVersion() {
		return FMLLoader.getCurrent().getVersionInfo().mcAndNeoFormVersion();
	}

	static Path gameDir() {
		return FMLLoader.getCurrent().getGameDir();
	}

	static boolean client() {
		return FMLLoader.getCurrent().getDist().isClient();
	}

	static boolean production() {
		return FMLLoader.getCurrent().isProduction();
	}

	/** The game's program arguments. */
	/** The module layers besides the mod files' whose packages a Fabric mod mustn't split: the JVM's boot layer. */
	static List<ModuleLayer> platformLayers() {
		return List.of(ModuleLayer.boot());
	}

	static String[] programArgs() {
		return FMLLoader.getCurrent().getProgramArgs().getArguments();
	}

	/** A file of a mod file, or null when it has none. */
	static byte[] read(IModFile file, String path) throws IOException {
		return file.getContents().readFile(path);
	}

	static boolean contains(IModFile file, String path) {
		return file.getContents().containsFile(path);
	}

	/** The packages of a mod file's module, '.'-separated. */
	static Set<String> packages(IModFile file) {
		return JarModuleInfo.scanModulePackages(file.getContents());
	}

	static Manifest manifest(IModFile file) {
		return file.getContents().getManifest();
	}

	/** The name of the module NeoForge makes of a mod file. */
	static String moduleName(IModFile file) {
		return file.getId();
	}

	/** Hands NeoForge a mod file made of the jars, each with the files the mod's module has; empty if NeoForge refuses it. */
	static Optional<IModFile> addModFile(IDiscoveryPipeline pipeline, List<FilteredPath> paths, ModFileDiscoveryAttributes attributes)
			throws IOException {
		List<JarContents.FilteredPath> filtered = paths.stream()
				.map(p -> new JarContents.FilteredPath(p.path(), p.keeps() == null ? null : p.keeps()::test))
				.toList();
		return pipeline.addJarContent(JarContents.ofFilteredPaths(filtered), attributes, IncompatibleFileReporting.ERROR);
	}
}
