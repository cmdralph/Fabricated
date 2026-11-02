package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * The forge-legacy test mod's part for fabric-rendering-v1's {@code HudRenderCallback} (HUD-2; with Fabric API's jar
 * only): Forge 47 draws the HUD in place of vanilla's method, at whose end upstream fires the callback, and Fabricated
 * fires it from Forge's HUD drawing instead. Reported the first time it fires (once the world shows); the client smoke
 * reports it missing otherwise.
 */
@Environment(EnvType.CLIENT)
public final class ForgeHudClient {
	/** Fired on the render thread only. */
	private static boolean fired;

	private ForgeHudClient() {
	}

	/** From the client entrypoint; only with Fabric API's rendering module. */
	static void register() {
		HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
			if (fired) return;
			fired = true;
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST hud render callback OK");
		});
	}
}
