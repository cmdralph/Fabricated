package io.github.cmdralph.fabricated.loader;

import java.util.List;
import java.util.Optional;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager;

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
			ProposedJars.install(found, gameFallback(found));
		});
		return loader;
	}

	/**
	 * The loader {@link ProposedJars} goes in front of, for the classes of the packages no module of the game's layers
	 * has: ModLauncher's boot layer's loader, with the launcher's libraries. Forge 47's ModLauncher makes it the game
	 * loader's fallback, as NeoForge 21.1's does; Forge 61 to 66's leaves the fallback unset and looks in every parent
	 * layer's loader. Vanilla reaches the packages their modules don't export through it: Gson's
	 * {@code com.google.gson.internal}, whose {@code Streams} reads a player's stats (GSON-1, GSON-2).
	 */
	private static ClassLoader gameFallback(ClassLoader game) {
		Launcher launcher = Launcher.INSTANCE;
		if (launcher == null) return game.getParent();
		return launcher.findLayerManager()
				.flatMap(manager -> manager.getLayer(IModuleLayerManager.Layer.BOOT))
				.flatMap(layer -> layer.modules().stream().findFirst())
				.map(Module::getClassLoader)
				.orElse(game.getParent());
	}
}
