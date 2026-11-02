package net.fabricmc.fabric.api.client.model.loading.v1;

import net.fabricmc.fabric.api.event.Event;

/** Compile-time stand-in: the members of Fabric API's 1.21.11 interface that the test mod calls, in Mojang's names. */
@FunctionalInterface
public interface ModelLoadingPlugin {
	static void register(ModelLoadingPlugin plugin) {
		throw new AssertionError("stub");
	}

	void initialize(Context pluginContext);

	interface Context {
		Event<ModelModifier.AfterBakeBlock> modifyBlockModelAfterBake();

		Event<ModelModifier.AfterBakeItem> modifyItemModelAfterBake();
	}
}
