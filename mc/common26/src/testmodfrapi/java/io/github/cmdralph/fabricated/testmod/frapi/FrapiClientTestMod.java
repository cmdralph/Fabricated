package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wraps the test block's baked model in a Fabric model (its own {@code emitQuads}), which NeoForge's block renderer
 * can't draw. Fabricated sends it through Fabric's renderer when a chunk section is meshed: the model then sees the
 * section's level, and its quads (the wrapped stone model's) go to the section's buffers.
 */
public final class FrapiClientTestMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		AtomicBoolean wrapped = new AtomicBoolean();
		ModelLoadingPlugin.register(plugin -> plugin.modifyBlockModelAfterBake().register((model, context) -> {
			if (!BuiltInRegistries.BLOCK.getKey(context.state().getBlock()).equals(FrapiTestMod.TERRAIN_BLOCK_ID)) return model;
			if (!wrapped.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi model wrapped ({})", model.getClass().getName());
			return new TerrainCheckModel(model);
		}));
	}

	private static final class TerrainCheckModel extends WrapperBlockStateModel {
		private static final AtomicBoolean CALLED = new AtomicBoolean();
		private static final AtomicBoolean REPORTED = new AtomicBoolean();

		TerrainCheckModel(BlockStateModel wrapped) {
			super(wrapped);
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
			if (!CALLED.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi emitQuads called (level {}, thread {})", level.getClass().getName(), Thread.currentThread().getName());
			}
			super.emitQuads(emitter, level, pos, state, random, cullTest);
			// Reached from a section being meshed, after every quad went to the section's buffers.
			if (level instanceof RenderSectionRegion && !REPORTED.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi terrain OK ({} on {})", state, Thread.currentThread().getName());
			}
		}
	}
}
