package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedModelDeserializer;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.block.CompositeBlockModel;

/**
 * Fabric models (their own {@code emitQuads}), which NeoForge's block renderer can't draw:
 * <ul>
 * <li>the test block's model, wrapped after baking: drawn in a chunk section, as a falling block and with the breaking
 * overlay, each reached from Fabric's renderer with a different level;</li>
 * <li>a custom model type ({@code fabric:type}) used as the part of the nested test block's multipart model: drawn in
 * a chunk section, inside a vanilla model.</li>
 * </ul>
 * Each logs once when it is drawn, after every quad went to the game. Also checks Fabric's custom model formats
 * next to NeoForge's: a model file read by a Fabric model deserializer, and a blockstate of NeoForge's composite type.
 */
public final class FrapiClientTestMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		AtomicBoolean wrapped = new AtomicBoolean();
		AtomicBoolean neoForgeType = new AtomicBoolean();
		ModelLoadingPlugin.register(plugin -> plugin.modifyBlockModelAfterBake().register((model, context) -> {
			Identifier block = BuiltInRegistries.BLOCK.getKey(context.state().getBlock());
			if (block.equals(FrapiTestMod.NEOFORGE_TYPE_BLOCK_ID) && !neoForgeType.getAndSet(true)) {
				boolean composite = model instanceof CompositeBlockModel;
				FrapiTestMod.LOGGER.info("FABRICATED-TEST neoforge blockstate type {} ({})", composite ? "OK" : "FAILED", model.getClass().getName());
			}
			if (!block.equals(FrapiTestMod.TERRAIN_BLOCK_ID)) return model;
			if (!wrapped.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi model wrapped ({})", model.getClass().getName());
			return new TerrainCheckModel(model);
		}));
		CustomUnbakedBlockStateModel.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "nested_check"), NestedCheckModel.Unbaked.CODEC);
		// {"fabric:type": "fabricated_testmod_frapi:delegate", "model": <a model>}: reads the inner model.
		AtomicBoolean deserialized = new AtomicBoolean();
		UnbakedModelDeserializer.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "delegate"), (json, context) -> {
			if (!deserialized.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST model deserializer OK");
			return context.deserialize(GsonHelper.getAsJsonObject(json, "model"), UnbakedModel.class);
		});
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
