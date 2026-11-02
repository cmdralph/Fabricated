package net.fabricmc.fabric.api.client.model.loading.v1;

import net.fabricmc.fabric.api.event.Event;

/**
 * Compile-time stand-in: the members of Fabric API 0.116's interface that Fabricated's test mods call, in Mojang's
 * names.
 */
@FunctionalInterface
public interface ModelLoadingPlugin {
	static void register(ModelLoadingPlugin plugin) {
		throw new AssertionError("stub");
	}

	void onInitializeModelLoader(Context pluginContext);

	interface Context {
		Event<ModelModifier.AfterBake> modifyModelAfterBake();
	}
}
