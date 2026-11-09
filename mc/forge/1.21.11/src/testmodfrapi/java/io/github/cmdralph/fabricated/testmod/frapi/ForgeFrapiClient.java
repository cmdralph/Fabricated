package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.concurrent.atomic.AtomicBoolean;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

/**
 * Forge's half of the renderer test mod (1.21.11), beside the shared one (mc/shared), whose entrypoint
 * mc/platform-forge.gradle adds: Fabric's level render events at the extraction of the block outline and at its end,
 * and the terrain block's level-aware particle sprite where Forge asks a model with its model data
 * ({@link ParticleSpriteCheck}, PART-4).
 */
public final class ForgeFrapiClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ParticleSpriteCheck.register();
		AtomicBoolean outline = new AtomicBoolean();
		WorldRenderEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register((context, hit) -> {
			if (!outline.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi outline extraction event OK");
		});
		AtomicBoolean extracted = new AtomicBoolean();
		WorldRenderEvents.END_EXTRACTION.register(context -> {
			if (!extracted.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi end extraction event OK");
		});
	}
}
