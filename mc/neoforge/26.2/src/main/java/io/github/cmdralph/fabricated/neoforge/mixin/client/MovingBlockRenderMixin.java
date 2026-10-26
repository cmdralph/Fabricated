package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.MovingBlockFeatureRenderer;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sends moving blocks (pushed by a piston, falling) whose model is a Fabric model through Fabric's renderer (see
 * {@link FabricBlockRendering}), into the moving block renderer's buffers.
 */
@Mixin(MovingBlockFeatureRenderer.class)
abstract class MovingBlockRenderMixin extends RenderTypeFeatureRenderer<MovingBlockFeatureRenderer.Submit> implements FabricBlockRendering.MovingBlockBuffers {
	@Shadow
	@Final
	private PoseStack poseStack;

	@Override
	public VertexConsumer fabricated$buffer(RenderType type) {
		return getVertexBuilder(type);
	}

	@Override
	public @Nullable VertexConsumer fabricated$outlineBuffer(RenderType type) {
		return type.outline().map(this::getVertexBuilder).orElse(null);
	}

	/**
	 * NeoForge draws the block unless its model is a Fabric model, which Fabric's renderer draws instead, as upstream
	 * does. An outlined block (a glowing falling block) is submitted again for its outline: Fabric's renderer draws that
	 * too, into the outline's buffers (M2b), as NeoForge would draw it from the model's parts, which a model with only
	 * {@code emitQuads} doesn't have.
	 */
	@WrapWithCondition(method = "buildGroup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"))
	private boolean fabricated$fabricModels(ModelBlockRenderer renderer, BlockQuadOutput output, float x, float y, float z,
			BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed,
			@Local(argsOnly = true) FeatureFrameContext context, @Local(name = "submit") MovingBlockFeatureRenderer.Submit submit) {
		if (!FabricBlockRendering.draws(model)) return true;
		// 26.2's submits never force translucency (26.3 adds that).
		FabricBlockRendering.tesselateMoving(this, poseStack.last(), false, submit.outlineColor(), context.options().ambientOcclusion, context.blockColors(),
				x, y, z, level, pos, state, model, seed);
		return false;
	}
}
