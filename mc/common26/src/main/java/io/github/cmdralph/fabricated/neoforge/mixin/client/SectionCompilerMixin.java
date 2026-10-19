package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.BufferBuilder;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricTerrain;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sends blocks whose model is a Fabric model through Fabric's renderer when a chunk section is meshed (see
 * {@link FabricTerrain}). NeoForge moved vanilla's {@code compile} into an overload that also takes its additional
 * section renderers; the old one only delegates, so this applies to the overload holding the code.
 *
 * <p>This runs for every block of every section: a condition (not a wrapped operation, whose call boxes its
 * arguments) keeps NeoForge's path free of allocations.
 */
@Mixin(SectionCompiler.class)
abstract class SectionCompilerMixin implements FabricTerrain.Layers {
	@Shadow
	@Final
	private boolean ambientOcclusion;
	@Shadow
	@Final
	private BlockColors blockColors;

	@Shadow
	protected abstract BufferBuilder getOrBeginLayer(Map<ChunkSectionLayer, BufferBuilder> startedLayers, SectionBufferBuilderPack builders, ChunkSectionLayer layer);

	@Override
	public BufferBuilder fabricated$layer(Map<ChunkSectionLayer, BufferBuilder> startedLayers, SectionBufferBuilderPack builders, ChunkSectionLayer layer) {
		return getOrBeginLayer(startedLayers, builders, layer);
	}

	/** NeoForge draws the block unless its model is a Fabric model, which Fabric's renderer draws instead. */
	// With the name alone Mixin would pick the first compile, the delegate.
	@WrapWithCondition(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"))
	private boolean fabricated$fabricModels(ModelBlockRenderer renderer, BlockQuadOutput output, float x, float y, float z,
			BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed,
			@Local(argsOnly = true) SectionBufferBuilderPack builders, @Local(name = "startedLayers") Map<ChunkSectionLayer, BufferBuilder> startedLayers) {
		if (!FabricTerrain.draws(model)) return true;
		FabricTerrain.tesselate(this, startedLayers, builders, ambientOcclusion, blockColors, x, y, z, level, pos, state, model, seed);
		return false;
	}
}
