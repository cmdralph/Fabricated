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
 * Fabric models (their own {@code emitQuads}), which NeoForge's block renderer can't draw: custom blockstate model types
 * ({@code fabric:type}) wrapping stone, one the test block's model, the other the part of the nested test block's vanilla
 * multipart model. Each logs once when a chunk section draws it (1.21.11).
 */
public final class FrapiClientTestMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CustomUnbakedBlockStateModel.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "terrain_check"), CheckModel.Unbaked.TERRAIN);
		CustomUnbakedBlockStateModel.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "nested_check"), CheckModel.Unbaked.NESTED);
	}

	/** Draws the model it wraps, and logs {@code check} the first time a chunk section draws it. */
	private static final class CheckModel extends DelegateBlockStateModel implements FabricBlockStateModel {
		private final String check;
		private final AtomicBoolean logged = new AtomicBoolean();

		CheckModel(BlockStateModel wrapped, String check) {
			super(wrapped);
			this.check = check;
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
			FabricBlockStateModel.super.emitQuads(emitter, level, pos, state, random, cullTest);
			if (level instanceof RenderSectionRegion && !logged.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST {} ({} in {} on {})", check, state, level.getClass().getName(), Thread.currentThread().getName());
			}
		}

		/** {@code {"fabric:type": "fabricated_testmod_frapi:terrain_check" or "...:nested_check", "model": <a variant>}}. */
		record Unbaked(BlockStateModel.Unbaked model, String check) implements CustomUnbakedBlockStateModel {
			static final MapCodec<Unbaked> TERRAIN = codec("frapi terrain OK");
			static final MapCodec<Unbaked> NESTED = codec("frapi nested terrain OK");

			private static MapCodec<Unbaked> codec(String check) {
				return BlockStateModel.Unbaked.CODEC.fieldOf("model").xmap(model -> new Unbaked(model, check), Unbaked::model);
			}

			@Override
			public MapCodec<Unbaked> codec() {
				return check.equals("frapi terrain OK") ? TERRAIN : NESTED;
			}

			@Override
			public BlockStateModel bake(ModelBaker baker) {
				return new CheckModel(model.bake(baker), check);
			}

			@Override
			public void resolveDependencies(Resolver resolver) {
				model.resolveDependencies(resolver);
			}
		}
	}
}
