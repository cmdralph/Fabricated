package net.fabricmc.fabric.api.renderer.v1;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.renderer.v1.render.BlockVertexConsumerProvider;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 interface that Fabricated calls, in Mojang's names.
 */
public interface Renderer {
	static Renderer get() {
		throw new AssertionError("stub");
	}

	void render(ModelBlockRenderer modelRenderer, BlockAndTintGetter blockView, BlockStateModel model, BlockState state, BlockPos pos, PoseStack matrices,
			BlockVertexConsumerProvider vertexConsumers, boolean cull, long seed, int overlay);
}
