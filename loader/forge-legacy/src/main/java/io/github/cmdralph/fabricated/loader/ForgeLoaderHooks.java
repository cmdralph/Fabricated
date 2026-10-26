package io.github.cmdralph.fabricated.loader;

import java.util.List;
import java.util.Optional;

/**
 * What the in-game mod's code needs of discovery on Forge (docs/PORTING.md §4.1). The in-game mod is a module of the
 * game layer and discovery's classes are in the service layer, a module of its own, so the package-private parts it
 * uses on NeoForge (where the containers are discovery's) are reached through here.
 */
public final class ForgeLoaderHooks {
	private static volatile List<String> notLoaded = List.of();

	private ForgeLoaderHooks() {
	}

	/**
	 * What the player is told about each Fabric mod discovery didn't load (its API modules aside), for Forge's loading
	 * warnings: discovery runs in the service layer, which can't reach them. Empty when discovery didn't run.
	 */
	public static List<String> notLoaded() {
		return notLoaded;
	}

	static void notLoaded(List<String> lines) {
		notLoaded = List.copyOf(lines);
	}

	/**
	 * Connects a translated Fabric mod's container to its classes: Fabric Loader's runtime gets the class loader of the
	 * mod's module, and Fabric mods can add jars to the class path from then on. Empty when discovery didn't run or the
	 * mod isn't one it translated, otherwise the class loader.
	 */
	public static Optional<ClassLoader> attach(String modId, ModuleLayer gameLayer, String moduleName) {
		FabricatedState state = FabricatedState.get();
		if (state == null || !state.isFabricMod(modId)) return Optional.empty();
		Optional<ClassLoader> loader = gameLayer.findModule(moduleName).map(Module::getClassLoader);
		loader.ifPresent(found -> {
			state.fabricContainer(modId).ifPresent(container -> state.runtime().setClassLoader(container, found));
			ProposedJars.install(found);
		});
		return loader;
	}
}
