package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The forge-legacy test mod's part for fabric-model-loading-api-v1's bake events (with Fabric API's jar only): a model
 * loading plugin's before-bake and after-bake modifiers must each see the models the game bakes. Forge 47 moved
 * {@code ModelBakerImpl.bake}'s code into an overload that also takes the sprite getter, where pre-flight points
 * upstream's hooks: the after-bake one as split-off code, the before-bake one, a variable modifier, as well (MODEL-3).
 * A client entrypoint of 1.20.1's own: Fabric API 0.77 has no bake events.
 */
@Environment(EnvType.CLIENT)
public final class ForgeModelClient implements ClientModInitializer {
	private static volatile boolean beforeBake;
	private static volatile boolean reported;

	/** Only with Fabric API's model loading module. */
	@Override
	public void onInitializeClient() {
		if (FabricLoader.getInstance().isModLoaded("fabric-model-loading-api-v1")) ForgeTestMod.run("model loading plugin", ForgeModelClient::register);
	}

	private static void register() {
		ModelLoadingPlugin.register(plugin -> {
			plugin.modifyModelBeforeBake().register((model, context) -> {
				if (context.id() != null) beforeBake = true;
				return model;
			});
			plugin.modifyModelAfterBake().register((model, context) -> {
				if (context.id() != null && !reported) {
					reported = true;
					if (beforeBake) {
						ForgeTestMod.LOGGER.info("FABRICATED-TEST model bake events OK");
					} else {
						ForgeTestMod.LOGGER.warn("FABRICATED-TEST model bake events: a model was baked without its before-bake event");
					}
				}
				return model;
			});
		});
	}
}
