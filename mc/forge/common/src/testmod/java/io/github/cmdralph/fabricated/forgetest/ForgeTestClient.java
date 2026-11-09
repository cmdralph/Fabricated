package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Forge's half of the CI test mod's client entrypoint, which Fabric Loader runs from {@code Minecraft}'s constructor
 * after the main ones: Fabric Loader's game instance is the {@code Minecraft} instance, a key mapping is registered,
 * checked once the game has started ({@link ForgeKeyMappingTest}), and with Fabric API's rendering module HUD elements
 * and the HUD callback on Forge 61, checked as they are drawn ({@link ForgeHudTest}), and with its particles module, on
 * Forge 61, a check of the block dust tint event ({@link ForgeParticleTintTest}).
 */
@Environment(EnvType.CLIENT)
public final class ForgeTestClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Object instance = FabricLoader.getInstance().getGameInstance();
		if (instance != null && instance == Minecraft.getInstance()) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST client game instance OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST client game instance FAILED: {}, not the Minecraft instance", instance);
		}
		// Native key mappings (key bindings on Forge 61), with Fabric API's jar or without it (see ForgeKeyMappingTest).
		try {
			ForgeKeyMappingTest.register();
		} catch (RuntimeException | LinkageError e) {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST key mapping FAILED: not registered", e);
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-rendering-v1")) {
			try {
				ForgeHudTest.register();
			} catch (RuntimeException | LinkageError e) {
				ForgeTestMod.LOGGER.error("FABRICATED-TEST HUD elements FAILED: not registered", e);
			}
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-particles-v1") && FabricLoader.getInstance().isModLoaded("fabric-networking-api-v1")) {
			try {
				ForgeParticleTintTest.register();
			} catch (RuntimeException | LinkageError e) {
				ForgeTestMod.LOGGER.error("FABRICATED-TEST block dust tint event FAILED: not registered", e);
			}
		}
	}
}
