package io.github.cmdralph.fabricated.neoforge.resource;

import net.fabricmc.fabric.api.resource.v1.DataResourceLoader;
import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.flag.FeatureFlagSet;

/** Fabric's shared server reload state for this Minecraft version (recipes and advancements are reload listeners here). */
final class ServerReloadState {
	private ServerReloadState() {
	}

	static void prepare(PreparableReloadListener.SharedState state, ReloadableServerResources resources, HolderLookup.Provider registries,
			FeatureFlagSet features, DataResourceStore.Mutable store) {
		state.set(DataResourceLoader.REGISTRY_LOOKUP_KEY, registries);
		state.set(DataResourceLoader.FEATURE_FLAG_SET_KEY, features);
		state.set(DataResourceLoader.DATA_RESOURCE_STORE_KEY, store);
		state.set(DataResourceLoader.ADVANCEMENT_LOADER_KEY, resources.getAdvancements());
		state.set(DataResourceLoader.RECIPE_MANAGER_KEY, resources.getRecipeManager());
	}
}
