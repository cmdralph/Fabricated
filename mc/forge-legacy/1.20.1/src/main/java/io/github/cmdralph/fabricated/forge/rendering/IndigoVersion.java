package io.github.cmdralph.fabricated.forge.rendering;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where Fabric API 0.92's Indigo, which Forge 47 runs, differs from 0.77's in what {@link FabricBlockRendering} hands it:
 * both its contexts apply a block's offset to the pose themselves, and a context says whether a transform is pushed.
 */
final class IndigoVersion {
	/** Indigo's block context applies the block's offset itself, which Forge's renderer applies again after it threw. */
	static final boolean BLOCK_CONTEXT_OFFSETS = true;

	private IndigoVersion() {
	}

	/** Before Indigo's terrain context draws a chunk section's block: nothing, as it applies the block's offset itself. */
	static void beforeTerrain(PoseStack poseStack, BlockState state, BlockAndTintGetter level, BlockPos pos) {
	}

	/** Pops the transforms a model that threw left pushed on a context (none are between two blocks). */
	static void popTransforms(RenderContext context) {
		for (int i = 0; i < 64 && context.hasTransform(); i++) context.popTransform();
	}
}
