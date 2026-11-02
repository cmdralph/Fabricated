package io.github.cmdralph.fabricated.forge.rendering.mixin;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * The breaking overlay over a block, and a block drawn as an entity or on its own, whose model is a Fabric model or a
 * composite with one, are drawn by Fabric's renderer, as upstream's hooks draw them: on Forge 61 pre-flight leaves those
 * hooks out (Forge moved both methods' bodies to overloads taking model data, so neither call they hook is there), and
 * Forge's renderer draws such a model's parts, which are often none. Every other block keeps Forge's path. Through the
 * overloads Forge's own callers and the old methods use.
 */
@Mixin(BlockRenderDispatcher.class)
abstract class BlockRenderDispatcherMixin {
	@Shadow
	@Final
	private BlockColors blockColors;

	@Shadow
	public abstract BlockStateModel getBlockModel(BlockState state);

	@WrapOperation(method = "renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;"
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraftforge/client/model/data/ModelData;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Ljava/util/List;"
			+ "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZI)V"))
	private void fabricated$drawBreakingOverlay(ModelBlockRenderer renderer, BlockAndTintGetter level, List<BlockModelPart> parts, BlockState state, BlockPos pos,
			PoseStack poseStack, VertexConsumer consumer, boolean cull, int overlay, Operation<Void> original) {
		BlockStateModel model = getBlockModel(state);
		if (FabricBlockRendering.draws(model)) {
			FabricBlockRendering.renderBreaking(renderer, level, model, state, pos, poseStack, consumer, cull, overlay);
		} else {
			original.call(renderer, level, parts, state, pos, poseStack, consumer, cull, overlay);
		}
	}

	@Inject(method = "renderSingleBlock(Lnet/minecraft/world/level/block/state/BlockState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II"
			+ "Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/rendertype/RenderType;)V", at = @At("HEAD"), cancellable = true)
	private void fabricated$drawAsEntity(BlockState state, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay, ModelData modelData,
			@Nullable RenderType renderType, CallbackInfo ci) {
		if (!FabricBlockRendering.ENABLED || state.getRenderShape() == RenderShape.INVISIBLE) return;
		BlockStateModel model = getBlockModel(state);
		if (!FabricBlockRendering.draws(model)) return;
		// The block's tint with no level, as Forge's method takes it.
		int tint = blockColors.getColor(state, null, null, 0);
		FabricBlockRendering.renderAsEntity(poseStack.last(), buffers, renderType, model, (tint >> 16 & 255) / 255.0F, (tint >> 8 & 255) / 255.0F,
				(tint & 255) / 255.0F, light, overlay, state);
		ci.cancel();
	}
}
