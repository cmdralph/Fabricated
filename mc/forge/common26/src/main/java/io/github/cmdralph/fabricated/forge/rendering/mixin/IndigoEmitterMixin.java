package io.github.cmdralph.fabricated.forge.rendering.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform;

/**
 * Indigo's emitter says how many transforms are pushed on it, so Fabricated's guard can pop those a model that threw
 * left behind (FRAPI rule 8): the emitter outlives the block, and the next block drawn into it would get them. Pseudo:
 * skipped without Indigo.
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl", remap = false)
abstract class IndigoEmitterMixin implements FabricBlockRendering.TransformDepth {
	@Shadow(remap = false)
	@Final
	private ObjectArrayList<QuadTransform> transformStack;

	@Override
	public int fabricated$transformDepth() {
		return transformStack.size();
	}
}
