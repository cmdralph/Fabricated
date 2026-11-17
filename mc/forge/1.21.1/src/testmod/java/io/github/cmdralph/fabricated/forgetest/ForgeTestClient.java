package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Forge 52's half of the test mod's client entrypoint: Fabric's game instance is the game's, and with Fabric API's
 * screen module the screen mouse and key events ({@link ForgeScreenClient}).
 */
public final class ForgeTestClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Object instance = FabricLoader.getInstance().getGameInstance();
		if (instance != null && instance == Minecraft.getInstance()) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST client game instance OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST client game instance FAILED: {}, not the Minecraft instance", instance);
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-screen-api-v1")) ForgeScreenClient.register();
	}
}
