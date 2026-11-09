package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.concurrent.atomic.AtomicBoolean;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.resources.Identifier;

/**
 * Forge's half of the renderer and client API test mod (26.x), beside the shared one (mc/shared), whose entrypoint
 * mc/platform-forge.gradle adds: Fabric's screen input events, which Fabricated fires from Forge's screen input hooks
 * ({@link ScreenInputCheck}), a HUD element attached before the hotbar, which Fabricated draws from Forge's HUD layer for
 * it (HUD-2), and the terrain block's level-aware particle material where Forge asks a model with its model data
 * ({@link ParticleSpriteCheck}, PART-4).
 */
public final class ForgeFrapiClient implements ClientModInitializer {
	private final ScreenInputCheck input = new ScreenInputCheck();
	private final AtomicBoolean beforeHotbar = new AtomicBoolean();
	private boolean reported;

	@Override
	public void onInitializeClient() {
		ParticleSpriteCheck.register();
		HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "before_hotbar"),
				(graphics, deltaTracker) -> {
					if (!beforeHotbar.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST hud element before the hotbar OK");
				});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			if (!beforeHotbar.get()) FrapiTestMod.LOGGER.error("FABRICATED-TEST hud element before the hotbar FAILED: never drawn");
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> input.listen(screen));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				input.tick(client);
				return;
			}
			if (reported || client.player.tickCount <= 40) return;
			reported = true;
			input.report();
		});
	}
}
