package net.fabricmc.fabric.api.client.model.loading.v1;

import net.fabricmc.fabric.api.event.Event;

/**
 * Compile-time stand-in: the members of Fabric API 0.92's interface (1.20.1) that the renderer test mod calls, in
 * Mojang's names. (The other test mod's are in Intermediary's, in src/testmodstubs, which comes first on its classpath.)
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
