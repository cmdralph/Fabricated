package net.fabricmc.fabric.impl.client.indigo.renderer.render;

import java.util.function.Function;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compile-time stand-in: the members of Indigo's (Fabric API 0.116) chunk section context that Fabricated calls, in
 * Mojang's names. Indigo's own hook that calls it doesn't apply on NeoForge 21.1 (FRAPI-5).
 */
public class TerrainRenderContext {
	public TerrainRenderContext() {
		throw new AssertionError("stub");
	}

	public void prepare(RenderChunkRegion blockView, Function<RenderType, BufferBuilder> bufferFunc) {
		throw new AssertionError("stub");
	}

	public void release() {
		throw new AssertionError("stub");
	}

	public void tessellateBlock(BlockState blockState, BlockPos blockPos, BakedModel model, PoseStack matrixStack) {
		throw new AssertionError("stub");
	}
}
