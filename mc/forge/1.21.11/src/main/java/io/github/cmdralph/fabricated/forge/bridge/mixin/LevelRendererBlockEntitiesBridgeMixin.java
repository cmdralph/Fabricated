package io.github.cmdralph.fabricated.forge.bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import io.github.cmdralph.fabricated.forge.bridge.EveryFrustum;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.LevelRenderState;

/**
 * Vanilla's {@code LevelRenderer.extractVisibleBlockEntities(Camera, float, LevelRenderState)}, for Fabric mods'
 * invokers of it (LEAVE-5): Iris's shadow pass extracts the block entities of the sections it draws through one.
 * Forge 61 gives the method a frustum, by which it culls each block entity's render box, as vanilla's method doesn't; the
 * rest of its code is vanilla's (read in its classes). Called with a frustum every box is visible in, it extracts what
 * vanilla's does. Pre-flight points such an invoker at the bridge here ({@code MixinRepairs.changedMethodBridge} in core).
 * In this version's folder: Forge 65 and 66 have no such method, and 26.1.2 keeps the state in another package.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererBlockEntitiesBridgeMixin {
	@Shadow
	private void extractVisibleBlockEntities(Camera camera, float partialTick, LevelRenderState state, Frustum frustum) {
	}

	/** Vanilla's {@code extractVisibleBlockEntities}: every block entity of the visible sections, none culled. */
	public void fabricated$invoke$extractVisibleBlockEntities(Camera camera, float partialTick, LevelRenderState state) {
		extractVisibleBlockEntities(camera, partialTick, state, EveryFrustum.INSTANCE);
	}
}
