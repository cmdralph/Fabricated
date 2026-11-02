package net.fabricmc.fabric.impl.client.indigo.renderer.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compile-time stand-in: the members of Indigo's (Fabric API 0.92, 1.20.1) context for blocks drawn outside chunk
 * sections that Fabricated calls, in Mojang's names. Indigo's own hook that calls it is on vanilla's overload, which
 * Forge 47's renderers don't call.
 */
public class BlockRenderContext {
	public BlockRenderContext() {
		throw new AssertionError("stub");
	}

	public void render(BlockAndTintGetter blockView, BakedModel model, BlockState state, BlockPos pos, PoseStack matrixStack, VertexConsumer buffer, boolean cull,
			RandomSource random, long seed, int overlay) {
		throw new AssertionError("stub");
	}
}
