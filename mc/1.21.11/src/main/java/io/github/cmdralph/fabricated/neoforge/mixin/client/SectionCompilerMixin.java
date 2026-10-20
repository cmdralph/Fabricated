package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.util.List;
import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws Fabric block models in chunk sections (1.21.11): NeoForge draws the block unless its model is a Fabric model,
 * which Fabric's renderer draws instead, into NeoForge's buffer for each quad's layer (see {@link FabricBlockRendering}).
 */
@Mixin(SectionCompiler.class)
abstract class SectionCompilerMixin {
	// With the name alone Mixin would pick the first compile, the delegate.
	@WrapWithCondition(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/util/function/Function;ZLjava/util/List;)V"))
	private boolean fabricated$fabricModels(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack poseStack,
			Function<ChunkSectionLayer, VertexConsumer> buffers, boolean cull, List<BlockModelPart> parts) {
		BlockStateModel model = dispatcher.getBlockModel(state);
		if (!FabricBlockRendering.draws(model)) return true;
		FabricBlockRendering.tesselate(dispatcher, level, model, state, pos, poseStack, buffers, cull);
		return false;
	}
}
