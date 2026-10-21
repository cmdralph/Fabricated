package net.fabricmc.loader.impl.entrypoint;

/**
 * Fabric Loader's internal entrypoint storage, which some mods take from {@code FabricLoaderImpl}'s private field to add
 * entrypoints of their own (FrozenLib). Fabricated's {@code FabricLoaderImpl} has no such field, so a mod's lookup
 * fails as it does on a loader without one; the class exists so the mod's code links and gets that far.
 */
public final class EntrypointStorage {
	private EntrypointStorage() {
	}
}
