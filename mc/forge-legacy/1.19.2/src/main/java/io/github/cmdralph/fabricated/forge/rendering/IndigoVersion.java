package io.github.cmdralph.fabricated.forge.rendering;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Where Fabric API 0.77's Indigo, which Forge 43 runs, differs from 0.92's in what {@link FabricBlockRendering} hands
 * it: its terrain context draws a block where the pose is, upstream's chunk section hook applying the block's offset
 * before it calls the context, and its block context draws the block where the pose is too, as upstream's hook into
 * vanilla's block renderer ran before vanilla applied the offset; and a context can't say whether a transform is
 * pushed.
 */
final class IndigoVersion {
	/** Indigo's block context draws the block where the pose is: no offset to take back after it threw. */
	static final boolean BLOCK_CONTEXT_OFFSETS = false;

	private IndigoVersion() {
	}

	/** Before Indigo's terrain context draws a chunk section's block: the block's offset, as upstream's hook applies it. */
	static void beforeTerrain(PoseStack poseStack, BlockState state, BlockAndTintGetter level, BlockPos pos) {
		Vec3 offset = state.getOffset(level, pos);
		poseStack.translate(offset.x, offset.y, offset.z);
	}

	/**
	 * Pops the transforms a model that threw left pushed on a context (none are between two blocks), until its stack is
	 * empty: popping an empty stack throws, before it changes anything.
	 */
	static void popTransforms(RenderContext context) {
		for (int i = 0; i < 64; i++) {
			try {
				context.popTransform();
			} catch (IndexOutOfBoundsException empty) {
				return;
			}
		}
	}
}
