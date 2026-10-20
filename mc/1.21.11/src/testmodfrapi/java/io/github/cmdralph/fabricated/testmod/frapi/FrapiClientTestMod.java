package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;

/**
 * A Fabric model (its own {@code emitQuads}), which NeoForge's block renderer can't draw: a custom blockstate model type
 * ({@code fabric:type}) wrapping stone, used by the test block. Logs once when a chunk section draws it (1.21.11).
 */
public final class FrapiClientTestMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CustomUnbakedBlockStateModel.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "terrain_check"), TerrainCheckModel.Unbaked.CODEC);
	}

	private static final class TerrainCheckModel extends DelegateBlockStateModel implements FabricBlockStateModel {
		private static final AtomicBoolean TERRAIN = new AtomicBoolean();

		TerrainCheckModel(BlockStateModel wrapped) {
			super(wrapped);
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
			FabricBlockStateModel.super.emitQuads(emitter, level, pos, state, random, cullTest);
			if (level instanceof RenderSectionRegion && !TERRAIN.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi terrain OK ({} in {} on {})", state, level.getClass().getName(), Thread.currentThread().getName());
			}
		}

		/** {@code {"fabric:type": "fabricated_testmod_frapi:terrain_check", "model": <a variant>}}. */
		record Unbaked(BlockStateModel.Unbaked model) implements CustomUnbakedBlockStateModel {
			static final MapCodec<Unbaked> CODEC = BlockStateModel.Unbaked.CODEC.fieldOf("model").xmap(Unbaked::new, Unbaked::model);

			@Override
			public MapCodec<Unbaked> codec() {
				return CODEC;
			}

			@Override
			public BlockStateModel bake(ModelBaker baker) {
				return new TerrainCheckModel(model.bake(baker));
			}

			@Override
			public void resolveDependencies(Resolver resolver) {
				model.resolveDependencies(resolver);
			}
		}
	}
}
