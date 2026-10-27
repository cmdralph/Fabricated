package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * The test mod's client entrypoint, which Fabric Loader runs from {@code Minecraft}'s constructor after the main ones,
 * the game instance set: each logs a line the client smoke looks for (ci/forge/smoke-client.sh).
 */
@Environment(EnvType.CLIENT)
public final class ForgeTestClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ForgeTestMod.LOGGER.info(ForgeTestMod.testItem != null
				? "FABRICATED-FORGE-TEST client entrypoint OK"
				: "FABRICATED-FORGE-TEST client entrypoint ran before the main entrypoint");
		Object instance = FabricLoader.getInstance().getGameInstance();
		if (instance != null && instance == Minecraft.getInstance()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST client game instance OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST client game instance is {}, not the Minecraft instance", instance);
		}
	}
}
