package net.fabricmc.fabric.impl.client.indigo.renderer.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compile-time stand-in: the member of Indigo's (Fabric API 0.92, 1.20.1) chunk section context that Fabricated calls,
 * in Mojang's names. Indigo's own hook prepares it for each compile (and lets go of it after); its hook that calls it
 * doesn't apply on Forge 47, whose compile calls another overload.
 */
public abstract class TerrainRenderContext implements RenderContext {
	public void tessellateBlock(BlockState blockState, BlockPos blockPos, BakedModel model, PoseStack matrixStack) {
		throw new AssertionError("stub");
	}
}
