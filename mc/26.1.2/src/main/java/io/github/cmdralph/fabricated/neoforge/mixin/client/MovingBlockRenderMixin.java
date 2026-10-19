package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.feature.BlockFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sends moving blocks (pushed by a piston, falling) whose model is a Fabric model through Fabric's renderer (see
 * {@link FabricBlockRendering}). On 26.1.2 the block feature renderer draws them, into the frame's buffers.
 */
@Mixin(BlockFeatureRenderer.class)
abstract class MovingBlockRenderMixin implements FabricBlockRendering.MovingBlockBuffers {
	/** The frame's buffers, while a Fabric model is drawn (render thread only). */
	@Unique
	private MultiBufferSource.@Nullable BufferSource fabricated$bufferSource;

	@Override
	public VertexConsumer fabricated$buffer(RenderType type) {
		return fabricated$bufferSource.getBuffer(type);
	}

	/** NeoForge draws the block unless its model is a Fabric model, which Fabric's renderer draws instead. */
	@WrapWithCondition(method = "renderMovingBlockSubmits", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"))
	private boolean fabricated$fabricModels(ModelBlockRenderer renderer, BlockQuadOutput output, float x, float y, float z,
			BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed,
			@Local(argsOnly = true) MultiBufferSource.BufferSource bufferSource, @Local(argsOnly = true) OptionsRenderState options,
			@Local(name = "poseStack") PoseStack poseStack) {
		if (!FabricBlockRendering.draws(model)) return true;
		fabricated$bufferSource = bufferSource;
		FabricBlockRendering.tesselateMoving(this, poseStack.last(), false, options.ambientOcclusion, Minecraft.getInstance().getBlockColors(),
				x, y, z, level, pos, state, model, seed);
		fabricated$bufferSource = null;
		return false;
	}
}
