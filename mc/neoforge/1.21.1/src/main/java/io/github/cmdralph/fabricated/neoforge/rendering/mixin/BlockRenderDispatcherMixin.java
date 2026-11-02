package io.github.cmdralph.fabricated.neoforge.rendering.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Sends the blocks NeoForge 21.1's block renderer draws one at a time, whose model is a Fabric model, through Fabric's
 * renderer (see {@link FabricBlockRendering}): the breaking overlay, and the blocks drawn through {@code renderBatched}
 * (other mods'; chunk sections already sent theirs). Upstream's hook is on vanilla's {@code tesselateBlock}, which these
 * no longer call.
 */
@Mixin(BlockRenderDispatcher.class)
abstract class BlockRenderDispatcherMixin {
	/** NeoForge's overload, with the model data and render type. */
	private static final String TESSELATE = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V";

	@WrapWithCondition(method = "renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/neoforged/neoforge/client/model/data/ModelData;)V",
			at = @At(value = "INVOKE", target = TESSELATE))
	private boolean fabricated$breakingOverlay(ModelBlockRenderer renderer, BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos,
			PoseStack poseStack, VertexConsumer buffer, boolean cull, RandomSource random, long seed, int overlay, ModelData modelData, @Nullable RenderType renderType) {
		return !FabricBlockRendering.draws(model)
				|| !FabricBlockRendering.tesselateBlock(level, model, state, pos, poseStack, buffer, cull, random, seed, overlay, modelData, renderType, "the breaking overlay");
	}

	/** Chunk sections call this for every block, once per render type: no lookup (the model is an argument). */
	@WrapWithCondition(method = "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
			at = @At(value = "INVOKE", target = TESSELATE))
	private boolean fabricated$batched(ModelBlockRenderer renderer, BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos,
			PoseStack poseStack, VertexConsumer buffer, boolean cull, RandomSource random, long seed, int overlay, ModelData modelData, @Nullable RenderType renderType) {
		return !FabricBlockRendering.draws(model)
				|| !FabricBlockRendering.tesselateBlock(level, model, state, pos, poseStack, buffer, cull, random, seed, overlay, modelData, renderType, "a block drawn on its own");
	}
}
