package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LightTexture;

/**
 * Has vanilla's {@code ParticleEngine.render} draw the particles on Forge 52 (IRIS-2). Forge keeps it, deprecated, for
 * its overload taking a frustum, which the game calls instead, but it calls itself where Forge meant that overload (its
 * patch passes no frustum), so a mod calling vanilla's method overflowed the stack: Iris's Fabric build does, for its
 * particle passes. The call is pointed at the overload with no frustum, as Forge 47 and NeoForge 21.1 make it. Where a
 * Forge 52 build makes that call itself, there is nothing to point.
 */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineRenderMixin {
	private static final String RENDER = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V";

	@Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;" + RENDER), require = 0)
	private void fabricated$renderOverload(ParticleEngine engine, LightTexture lightTexture, Camera camera, float partialTick) {
		engine.render(lightTexture, camera, partialTick, null);
	}
}
