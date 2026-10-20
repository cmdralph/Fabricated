package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.util.List;
import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.BlockFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sends moving blocks (pushed by a piston, falling) whose model is a Fabric model through Fabric's renderer (1.21.11; see
 * {@link FabricBlockRendering}): NeoForge draws the block's parts, which a Fabric model doesn't have, into a buffer per
 * part's layer, which Fabric's renderer is given too.
 */
@Mixin(BlockFeatureRenderer.class)
abstract class MovingBlockRenderMixin {
	@WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Ljava/util/List;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/util/function/Function;ZI)V"))
	private boolean fabricated$fabricModels(ModelBlockRenderer renderer, BlockAndTintGetter level, List<BlockModelPart> parts, BlockState state, BlockPos pos,
			PoseStack poseStack, Function<ChunkSectionLayer, VertexConsumer> buffers, boolean cull, int overlay,
			@Local(argsOnly = true) BlockRenderDispatcher dispatcher) {
		if (!FabricBlockRendering.ENABLED) return true;
		BlockStateModel model = dispatcher.getBlockModel(state);
		if (!FabricBlockRendering.draws(model)) return true;
		BlockPos seedPos = level instanceof MovingBlockRenderState moving ? moving.randomSeedPos : pos;
		FabricBlockRendering.tesselate(dispatcher, level, model, state, pos, poseStack, buffers, cull, state.getSeed(seedPos));
		return false;
	}
}
