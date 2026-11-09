package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * The client entrypoint of Forge 47's half of the test mod, which Fabric Loader runs from {@code Minecraft}'s
 * constructor after the main ones, the game instance set: it registers a key binding ({@link ForgeKeyBindingClient}), and
 * with Fabric API's model loading, screen and rendering modules it checks the model bake events
 * ({@link ForgeModelClient}), the screen mouse and key events ({@link ForgeScreenClient}) and the HUD callback
 * ({@link ForgeHudClient}).
 */
@Environment(EnvType.CLIENT)
public final class ForgeTestClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Object instance = FabricLoader.getInstance().getGameInstance();
		if (instance != null && instance == Minecraft.getInstance()) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST client game instance OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST client game instance FAILED: it is {}, not the Minecraft instance", instance);
		}
		ForgeTestMod.run("key binding registration", ForgeKeyBindingClient::register);
		if (FabricLoader.getInstance().isModLoaded("fabric-model-loading-api-v1")) ForgeTestMod.run("model loading plugin", ForgeModelClient::register);
		if (FabricLoader.getInstance().isModLoaded("fabric-screen-api-v1")) ForgeTestMod.run("screen listeners", ForgeScreenClient::register);
		if (FabricLoader.getInstance().isModLoaded("fabric-rendering-v1")) ForgeTestMod.run("HUD callback", ForgeHudClient::register);
	}
}
