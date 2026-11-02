package io.github.cmdralph.fabricated.neoforge.rendering.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Sends blocks a piston moves, whose model is a Fabric model, through Fabric's renderer (see
 * {@link FabricBlockRendering}): NeoForge 21.1's piston renderer draws them here, once per render type, with its own
 * {@code tesselateBlock}, which upstream's hook isn't on.
 */
@Mixin(ClientHooks.class)
abstract class ClientHooksMixin {
	/** NeoForge's overload, with the model data and render type. */
	private static final String TESSELATE = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V";

	@WrapWithCondition(method = "renderPistonMovedBlocks(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;ZILnet/minecraft/client/renderer/block/BlockRenderDispatcher;)V",
			at = @At(value = "INVOKE", target = TESSELATE))
	private static boolean fabricated$fabricModels(ModelBlockRenderer renderer, BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos,
			PoseStack poseStack, VertexConsumer buffer, boolean cull, RandomSource random, long seed, int overlay, ModelData modelData, @Nullable RenderType renderType) {
		return !FabricBlockRendering.draws(model)
				|| !FabricBlockRendering.tesselateBlock(level, model, state, pos, poseStack, buffer, cull, random, seed, overlay, modelData, renderType, "a block moved by a piston");
	}
}
