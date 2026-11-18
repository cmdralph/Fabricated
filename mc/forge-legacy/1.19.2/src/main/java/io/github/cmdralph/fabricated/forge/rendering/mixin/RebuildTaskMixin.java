package io.github.cmdralph.fabricated.forge.rendering.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Draws Fabric block models in the chunk sections Forge 43 compiles, as Forge 47's does (see
 * {@link FabricBlockRendering}): Forge 43's compiler calls the overload of {@code renderBatched} that also takes whether
 * to ask the model for its data, Forge 47's the one without. Indigo's own hook redirects vanilla's call, which Forge
 * replaced with this one; the call is Forge's, which keeps its name in SRG's.
 */
@Mixin(targets = "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask")
abstract class RebuildTaskMixin {
	private static final String COMPILE = "compile(FFFLnet/minecraft/client/renderer/ChunkBufferBuilderPack;)Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher$RenderChunk$RebuildTask$CompileResults;";

	/** Runs for every block of every section, once per render type: the model is the compiler's own local, no lookup. */
	@WrapWithCondition(method = COMPILE, at = @At(value = "INVOKE", remap = false,
			target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;Z)V"))
	private boolean fabricated$fabricModels(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack poseStack,
			VertexConsumer buffer, boolean cull, RandomSource random, ModelData modelData, @Nullable RenderType renderType, boolean queryModelData,
			@Local BakedModel model) {
		if (!FabricBlockRendering.draws(model) || !(level instanceof RenderChunkRegion region)) return true; // draws is false without the API, before any lookup
		// A model that threw is drawn by Forge instead (FRAPI rule 8).
		return !FabricBlockRendering.tesselate(region, state, pos, poseStack, model);
	}

	/** This thread lets go of the compile's region. (Indigo's hook lets go of its terrain context.) */
	@Inject(method = COMPILE, at = @At("RETURN"))
	private void fabricated$endSection(CallbackInfoReturnable<?> cir) {
		FabricBlockRendering.endSection();
	}
}
