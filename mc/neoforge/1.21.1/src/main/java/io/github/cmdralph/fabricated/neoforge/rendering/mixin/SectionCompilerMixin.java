package io.github.cmdralph.fabricated.neoforge.rendering.mixin;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws Fabric block models in the chunk sections NeoForge 21.1 compiles: NeoForge draws the block, once per render type
 * its model has, unless its model is a Fabric model, which Fabric's renderer draws instead, into the section's buffer for
 * each quad's layer (see {@link FabricBlockRendering}). Indigo's own hook redirects vanilla's call, which NeoForge
 * replaced with this one.
 */
@Mixin(SectionCompiler.class)
abstract class SectionCompilerMixin implements FabricBlockRendering.SectionLayers {
	// With the name alone Mixin would pick the first compile, the delegate.
	private static final String COMPILE = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;";

	@Shadow
	abstract BufferBuilder getOrBeginLayer(Map<RenderType, BufferBuilder> layers, SectionBufferBuilderPack pack, RenderType layer);

	@Override
	public BufferBuilder fabricated$layer(Map<RenderType, BufferBuilder> layers, SectionBufferBuilderPack pack, RenderType layer) {
		return getOrBeginLayer(layers, pack, layer);
	}

	/** Runs for every block of every section, once per render type: the model is the compiler's own local, no lookup. */
	@WrapWithCondition(method = COMPILE, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V"))
	private boolean fabricated$fabricModels(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack poseStack,
			VertexConsumer buffer, boolean cull, RandomSource random, ModelData modelData, @Nullable RenderType renderType,
			@Local(argsOnly = true) RenderChunkRegion region, @Local(argsOnly = true) SectionBufferBuilderPack pack,
			@Local Map<RenderType, BufferBuilder> layers, @Local BakedModel model) {
		if (!FabricBlockRendering.draws(model)) return true; // draws is false without the API, before any lookup
		// A model that threw is drawn by NeoForge instead (FRAPI rule 8).
		return !FabricBlockRendering.tesselate(this, layers, pack, region, state, pos, poseStack, model);
	}

	/** Lets go of the compile's region and buffers, which this thread's terrain context kept while it drew Fabric models. */
	@Inject(method = COMPILE, at = @At("RETURN"))
	private void fabricated$endSection(CallbackInfoReturnable<SectionCompiler.Results> cir) {
		FabricBlockRendering.endSection();
	}
}
