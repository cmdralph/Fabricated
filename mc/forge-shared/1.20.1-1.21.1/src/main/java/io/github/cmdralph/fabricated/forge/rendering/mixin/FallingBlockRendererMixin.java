package io.github.cmdralph.fabricated.forge.rendering.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Sends falling blocks whose model is a Fabric model through Fabric's renderer (see {@link FabricBlockRendering}):
 * Forge 47 and 52 draw them once per render type (as its moving block type), with Forge's own {@code tesselateBlock},
 * which upstream's hook isn't on. The call is Forge's overload, which on Forge 47 keeps its name in SRG's: only the
 * method is remapped.
 */
@Mixin(FallingBlockRenderer.class)
abstract class FallingBlockRendererMixin {
	/** Forge's overload, with the model data and render type. */
	private static final String TESSELATE = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V";

	@WrapWithCondition(method = "render(Lnet/minecraft/world/entity/item/FallingBlockEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE", target = TESSELATE, remap = false))
	private boolean fabricated$fabricModels(ModelBlockRenderer renderer, BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos,
			PoseStack poseStack, VertexConsumer buffer, boolean cull, RandomSource random, long seed, int overlay, ModelData modelData, @Nullable RenderType renderType) {
		return !FabricBlockRendering.draws(model)
				|| !FabricBlockRendering.tesselateBlock(level, model, state, pos, poseStack, buffer, cull, random, seed, overlay, modelData, renderType, "a falling block");
	}
}
