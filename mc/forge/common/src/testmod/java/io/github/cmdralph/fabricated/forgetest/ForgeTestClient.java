package io.github.cmdralph.fabricated.forgetest;

import io.github.cmdralph.fabricated.api.Fabricated;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * The test mod's client entrypoint, which Fabric Loader runs from {@code Minecraft}'s constructor after the main ones,
 * the game instance set: each logs a line the client smoke looks for (ci/forge/smoke-client.sh). With Fabric API's
 * networking module it also starts a networking round trip with the integrated server ({@link ForgeNetworkingClient}),
 * and with its particles module too, on Forge 61, a check of the block dust tint event ({@link ForgeParticleTintTest}).
 * It registers a key mapping, checked once the game has started ({@link ForgeKeyMappingTest}), and with Fabric API's
 * rendering module, on Forge 61, HUD elements and the HUD callback, checked as they are drawn ({@link ForgeHudTest}).
 * With Fabric API's resource loader it registers a built-in pack enabled by default ({@link ForgeDefaultPackTest}).
 */
@Environment(EnvType.CLIENT)
public final class ForgeTestClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Its Mod Menu entrypoint's screen behind Forge's mod list button, once the initializers ran (see ForgeModMenuEntry).
		Fabricated.get().ifPresent(api -> api.afterEntrypoints(() -> {
			if (ForgeModMenuEntry.registered()) ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST modmenu config screen OK");
			else ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST modmenu config screen FAILED: Forge's mod list has no config screen for the test mod");
		}));
		ForgeTestMod.LOGGER.info(ForgeTestMod.testItem != null
				? "FABRICATED-FORGE-TEST client entrypoint OK"
				: "FABRICATED-FORGE-TEST client entrypoint ran before the main entrypoint");
		Object instance = FabricLoader.getInstance().getGameInstance();
		if (instance != null && instance == Minecraft.getInstance()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST client game instance OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST client game instance is {}, not the Minecraft instance", instance);
		}
		// Native key mappings (key bindings on Forge 61), with Fabric API's jar or without it (see ForgeKeyMappingTest).
		try {
			ForgeKeyMappingTest.register();
		} catch (RuntimeException | LinkageError e) {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST key mapping not registered", e);
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-resource-loader-v1")) {
			try {
				ForgeDefaultPackTest.register();
			} catch (RuntimeException | LinkageError e) {
				ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST builtin resource pack not registered", e);
			}
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-networking-api-v1")) {
			try {
				ForgeNetworkingClient.register();
			} catch (RuntimeException | LinkageError e) {
				ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST networking client receiver not registered", e);
			}
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-rendering-v1")) {
			try {
				ForgeHudTest.register();
			} catch (RuntimeException | LinkageError e) {
				ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST HUD elements not registered", e);
			}
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-particles-v1") && FabricLoader.getInstance().isModLoaded("fabric-networking-api-v1")) {
			try {
				ForgeParticleTintTest.register();
			} catch (RuntimeException | LinkageError e) {
				ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST block dust tint callback not registered", e);
			}
		}
	}
}
