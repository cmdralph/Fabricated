package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;

/**
 * The forge-legacy test mod's part for fabric-model-loading-api-v1's bake events (with Fabric API's jar only): a model
 * loading plugin's before-bake and after-bake modifiers must each see the models the game bakes. Forge 47 moved
 * {@code ModelBakerImpl.bake}'s code into an overload that also takes the sprite getter, where pre-flight points
 * upstream's hooks: the after-bake one as split-off code, the before-bake one, a variable modifier, as well (MODEL-3).
 */
@Environment(EnvType.CLIENT)
public final class ForgeModelClient {
	private static volatile boolean beforeBake;
	private static volatile boolean reported;

	private ForgeModelClient() {
	}

	/** From the client entrypoint; only with Fabric API's model loading module. */
	static void register() {
		ModelLoadingPlugin.register(plugin -> {
			plugin.modifyModelBeforeBake().register((model, context) -> {
				if (context.id() != null) beforeBake = true;
				return model;
			});
			plugin.modifyModelAfterBake().register((model, context) -> {
				if (context.id() != null && !reported) {
					reported = true;
					if (beforeBake) {
						ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST model bake events OK");
					} else {
						ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST model bake events: a model was baked without its before-bake event");
					}
				}
				return model;
			});
		});
	}
}
