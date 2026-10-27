package net.fabricmc.fabric.api.client.model.loading.v1;

import net.fabricmc.fabric.api.event.Event;

@FunctionalInterface
public interface ModelLoadingPlugin {
	static void register(ModelLoadingPlugin plugin) {
		throw new AssertionError("stand-in");
	}

	void onInitializeModelLoader(Context pluginContext);

	interface Context {
		Event<ModelModifier.BeforeBake> modifyModelBeforeBake();

		Event<ModelModifier.AfterBake> modifyModelAfterBake();
	}
}
