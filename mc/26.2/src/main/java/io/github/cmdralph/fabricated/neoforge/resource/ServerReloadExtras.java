package io.github.cmdralph.fabricated.neoforge.resource;

import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import net.fabricmc.fabric.api.resource.v1.DataResourceLoader;

/** Version-specific shared reload state (this version still loads recipes and advancements as reload listeners). */
final class ServerReloadExtras {
	private ServerReloadExtras() {
	}

	static void prepare(ReloadableServerResources resources, PreparableReloadListener.SharedState state) {
		state.set(DataResourceLoader.ADVANCEMENT_LOADER_KEY, resources.getAdvancements());
		state.set(DataResourceLoader.RECIPE_MANAGER_KEY, resources.getRecipeManager());
	}
}
