package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric models (their own {@code emitQuads}), which NeoForge's block renderer can't draw:
 * <ul>
 * <li>the test block's model, wrapped after baking: drawn in a chunk section, as a falling block and with the breaking
 * overlay, each reached from Fabric's renderer with a different level;</li>
 * <li>a custom model type ({@code fabric:type}) used as the part of the nested test block's multipart model: drawn in
 * a chunk section, inside a vanilla model.</li>
 * </ul>
 * Each logs once when it is drawn, after every quad went to the game.
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
		CustomUnbakedBlockStateModel.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "nested_check"), NestedCheckModel.Unbaked.CODEC);
	}

	private static final class TerrainCheckModel extends WrapperBlockStateModel {
		private static final AtomicBoolean TERRAIN = new AtomicBoolean();
		private static final AtomicBoolean MOVING = new AtomicBoolean();
		private static final AtomicBoolean BREAKING = new AtomicBoolean();

		TerrainCheckModel(BlockStateModel wrapped) {
			super(wrapped);
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
			super.emitQuads(emitter, level, pos, state, random, cullTest);
			if (level instanceof RenderSectionRegion && !TERRAIN.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi terrain OK ({} on {})", state, Thread.currentThread().getName());
			} else if (level instanceof MovingBlockRenderState && !MOVING.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi moving block OK ({})", state);
			} else if (level == BlockAndTintGetter.EMPTY && !BREAKING.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi breaking OK ({})", state);
			}
		}
	}

	private static final class NestedCheckModel extends WrapperBlockStateModel {
		private static final AtomicBoolean TERRAIN = new AtomicBoolean();

		NestedCheckModel(BlockStateModel wrapped) {
			super(wrapped);
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
			super.emitQuads(emitter, level, pos, state, random, cullTest);
			if (level instanceof RenderSectionRegion && !TERRAIN.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi nested terrain OK ({} on {})", state, Thread.currentThread().getName());
			}
		}

		/** {@code {"fabric:type": "fabricated_testmod_frapi:nested_check", "model": <a variant>}}. */
		record Unbaked(BlockStateModel.Unbaked model) implements CustomUnbakedBlockStateModel {
			static final MapCodec<Unbaked> CODEC = BlockStateModel.Unbaked.CODEC.fieldOf("model").xmap(Unbaked::new, Unbaked::model);

			@Override
			public MapCodec<Unbaked> codec() {
				return CODEC;
			}

			@Override
			public BlockStateModel bake(ModelBaker baker) {
				return new NestedCheckModel(model.bake(baker));
			}

			@Override
			public void resolveDependencies(Resolver resolver) {
				model.resolveDependencies(resolver);
			}
		}
	}
}
