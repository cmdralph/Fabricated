package io.github.cmdralph.fabricated.forge.rendering;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.render.FabricOrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/**
 * The renderer API's breaking overlay hook on 26.2, for a Fabric model, through Fabricated's guard (FRAPI rule 8): the
 * model's quads into a mesh, with the same arguments as upstream's hook, submitted with the overlay's parts. A model
 * that throws, or is broken, gives its parts instead ({@link FabricBlockRendering#emit}). The mesh is this class's own,
 * one per thread (the render thread), and a transform a model left pushed reaches no other block (the guard's
 * {@link FabricBlockRendering#restore}).
 */
public final class BreakingOverlay {
	/** The overlay draws the whole model: no face is culled. */
	private static final Predicate<@Nullable Direction> NO_CULLING = direction -> false;
	private static final ThreadLocal<MutableMesh> MESH = ThreadLocal.withInitial(() -> Renderer.get().mutableMesh());

	private BreakingOverlay() {
	}

	public static void submit(SubmitNodeCollector collector, PoseStack poseStack, List<BlockStateModelPart> parts, int progress, RandomSource random,
			BlockBreakingRenderState state, BlockStateModel model) {
		MutableMesh mesh = MESH.get();
		mesh.clear();
		FabricBlockRendering.emit(model, mesh.emitter(), BlockAndTintGetter.EMPTY, state.blockPos(), state.blockState(), random, NO_CULLING,
				FabricBlockRendering.BREAKING);
		((FabricOrderedSubmitNodeCollector) collector).submitBreakingBlockModel(poseStack, parts, mesh.immutableCopy(), progress);
	}
}
