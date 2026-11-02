package io.github.cmdralph.fabricated.forge.rendering.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.feature.BlockFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A moving block (a falling block, a piston's) whose model is a Fabric model, or a composite with one, is drawn by
 * Fabric's renderer, as upstream's hook draws it: on Forge 61 pre-flight leaves that hook out (it captures an iterator
 * local the recompiled class doesn't name), and vanilla's renderer draws such a model's parts, which are often none.
 * Every other block keeps vanilla's call; the moving block buffer vanilla already asked for stays empty for this one.
 */
@Mixin(BlockFeatureRenderer.class)
abstract class MovingBlockMixin {
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Ljava/util/List;"
			+ "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZI)V"))
	private void fabricated$drawMovingBlock(ModelBlockRenderer renderer, BlockAndTintGetter level, List<BlockModelPart> parts, BlockState state, BlockPos pos,
			PoseStack poseStack, VertexConsumer consumer, boolean cull, int overlay, Operation<Void> original,
			@Local(argsOnly = true) MultiBufferSource.BufferSource buffers, @Local(argsOnly = true) BlockRenderDispatcher dispatcher) {
		BlockStateModel model = dispatcher.getBlockModel(state);
		if (!FabricBlockRendering.draws(model)) {
			original.call(renderer, level, parts, state, pos, poseStack, consumer, cull, overlay);
			return;
		}
		// The seed vanilla collected the parts with, as upstream's hook takes it.
		long seed = state.getSeed(level instanceof MovingBlockRenderState moving ? moving.randomSeedPos : pos);
		FabricBlockRendering.renderMoving(renderer, level, model, state, pos, poseStack, buffers, cull, seed, overlay);
	}
}
