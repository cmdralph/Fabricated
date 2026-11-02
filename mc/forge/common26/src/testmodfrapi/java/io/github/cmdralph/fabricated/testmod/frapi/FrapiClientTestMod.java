package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedModelDeserializer;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.client.renderer.v1.render.FabricLayerRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric models (their own {@code emitQuads}), which Forge's block renderer can't draw, on Forge 64 to 66 (as NeoForge's
 * renderer test mod checks them on NeoForge; Forge draws them through upstream's hooks):
 * <ul>
 * <li>the test block's model, wrapped after baking: drawn in a chunk section, as a falling block (and again for the
 * glowing falling block's outline, from 26.2 on), with the breaking overlay and in a minecart (a block drawn as an
 * entity), each reached from Fabric's renderer with a different level, or from a different caller;</li>
 * <li>a custom model type ({@code fabric:type}) used as the part of the nested test block's multipart model: drawn in
 * a chunk section, inside a vanilla model.</li>
 * </ul>
 * A chunk section is vanilla's, or Sodium's when Sodium meshes the world. Each logs once when it is drawn, after every
 * quad went to the game. Also checks a model file read by a Fabric model deserializer.
 *
 * <p>Two more Fabric models throw once a vanilla chunk section draws them, after emitting their quads and pushing a
 * transform they never pop (FRAPI rule 8): one the broken test block's model, the other a part of the broken nested
 * test block's multipart model. Each logs when it is then asked for its parts, and the transform logs a failure if any
 * later quad reaches it. A third does the same the first time it is drawn as an entity (the block a minecart carries).
 * They throw only where Fabricated guards the call (its {@code FabricBlockRendering} on the stack), not into another
 * renderer's hooks. A fourth throws in a chunk section
 * inside a Fabric model wrapping it ({@link WrapperCheckModel}), as Continuity's models wrap others: the throw is the
 * wrapped model's, so the wrapper's class keeps Fabric's renderer.
 *
 * <p>The test item's model is wrapped after it is baked ({@link ItemCheckModel}): it adds a layer of quads emitted
 * through Fabric's renderer API, and logs once when it emits them for the hotbar (a screen), a hand, a dropped item and
 * an item frame ({@link FrapiTestMod} puts it in each).
 *
 * <p>With Continuity installed, glass's model is wrapped after every other wrapper, Continuity's included
 * ({@link ConnectedCheckModel}), to check that its connected textures reach the chunk section.
 */
public final class FrapiClientTestMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LevelRenderEvents.START_MAIN.register(context -> TerrainCheckModel.frames++);
		AtomicBoolean wrapped = new AtomicBoolean();
		ModelLoadingPlugin.register(plugin -> plugin.modifyBlockModelAfterBake().register((model, context) -> {
			Identifier block = BuiltInRegistries.BLOCK.getKey(context.state().getBlock());
			if (block.equals(FrapiTestMod.WRAPPED_BROKEN_BLOCK_ID)) return new WrapperCheckModel(new BrokenWrappedModel(model));
			if (!block.equals(FrapiTestMod.TERRAIN_BLOCK_ID)) return model;
			if (!wrapped.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi model wrapped ({})", model.getClass().getName());
			return new TerrainCheckModel(model);
		}));
		ModelLoadingPlugin.register(plugin -> plugin.modifyItemModelAfterBake().register((model, context) ->
				context.itemId().equals(FrapiTestMod.FABRIC_ITEM_ID) ? new ItemCheckModel(model) : model));
		if (FabricLoader.getInstance().isModLoaded("continuity")) {
			ModelLoadingPlugin.register(plugin -> plugin.modifyBlockModelAfterBake().register(ModelModifier.WRAP_LAST_PHASE, (model, context) ->
					context.state().is(Blocks.GLASS) ? new ConnectedCheckModel(model) : model));
		}
		CustomUnbakedBlockStateModel.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "nested_check"), NestedCheckModel.Unbaked.CODEC);
		CustomUnbakedBlockStateModel.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "broken_check"), BrokenModel.CODEC);
		CustomUnbakedBlockStateModel.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "broken_part_check"), BrokenPartModel.CODEC);
		CustomUnbakedBlockStateModel.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "broken_entity_check"), BrokenEntityModel.CODEC);
		// {"fabric:type": "fabricated_testmod_frapi:delegate", "model": <a model>}: reads the inner model.
		AtomicBoolean deserialized = new AtomicBoolean();
		UnbakedModelDeserializer.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "delegate"), (json, context) -> {
			if (!deserialized.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST model deserializer OK");
			return context.deserialize(GsonHelper.getAsJsonObject(json, "model"), UnbakedModel.class);
		});
	}

	/**
	 * Which seam sent the block being drawn to Fabric's renderer: {@code "Fabricated"} when the call went through
	 * Fabricated's {@code FabricBlockRendering} (on Forge, its guard around upstream's hooks), or {@code "other"} (upstream's
	 * hooks alone, or another mod's). Walks the stack: logged once per check.
	 */
	static String seam() {
		return StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getClassName().startsWith("io.github.cmdralph.fabricated.")
				&& frame.getClassName().endsWith(".rendering.FabricBlockRendering"))) ? "Fabricated" : "other";
	}

	/** Whether a level is a chunk section's: vanilla's, or Sodium's when Sodium meshes the world. */
	static boolean isSection(BlockAndTintGetter level) {
		return level instanceof RenderSectionRegion || level.getClass().getName().equals("net.caffeinemc.mods.sodium.client.world.LevelSlice");
	}

	/** The game's class filling a block's render state when the block is drawn as an entity (a minecart's, an enderman's). */
	private static final String ENTITY_BLOCK_MODEL = "net.minecraft.client.renderer.block.model.BlockStateModelWrapper";

	/**
	 * Whether a block model is drawn as an entity: with no level (as the breaking overlay is too), called from the class
	 * filling an entity's block render state. Walks the stack: test code only.
	 */
	static boolean drawnAsEntity(BlockAndTintGetter level) {
		return level == BlockAndTintGetter.EMPTY
				&& StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getClassName().equals(ENTITY_BLOCK_MODEL)));
	}

	private static final class TerrainCheckModel extends WrapperBlockStateModel {
		private static final AtomicBoolean TERRAIN = new AtomicBoolean();
		private static final AtomicBoolean MOVING = new AtomicBoolean();
		private static final AtomicBoolean OUTLINE = new AtomicBoolean();
		/** Frames the game began drawing the level (render thread), to tell a block's outline pass from the next frame. */
		static long frames;
		private static long movingFrame = -1;
		private static @Nullable BlockPos movingPos;
		private static final AtomicBoolean BREAKING = new AtomicBoolean();
		private static final AtomicBoolean ENTITY = new AtomicBoolean();

		TerrainCheckModel(BlockStateModel wrapped) {
			super(wrapped);
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
			super.emitQuads(emitter, level, pos, state, random, cullTest);
			if (isSection(level) && !TERRAIN.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi terrain OK ({} in {} on {}, seam={})", state, level.getClass().getName(), Thread.currentThread().getName(),
						seam());
			} else if (level instanceof MovingBlockRenderState) {
				if (!MOVING.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi moving block OK ({})", state);
				checkOutline(pos);
			} else if (level == BlockAndTintGetter.EMPTY && !(BREAKING.get() && ENTITY.get())) {
				// No level: the breaking overlay, or a minecart's block.
				if (drawnAsEntity(level)) {
					if (!ENTITY.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi entity block OK ({})", state);
				} else if (!BREAKING.getAndSet(true)) {
					FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi breaking OK ({})", state);
				}
			}
		}

		/**
		 * Logs once that a moving block reached Fabric's renderer twice in one frame: the glowing falling block is drawn,
		 * then drawn again for its outline, by Fabric's renderer, not from the model's parts (M2b).
		 */
		private static void checkOutline(BlockPos pos) {
			if (frames > 0 && frames == movingFrame && pos.equals(movingPos) && !OUTLINE.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi moving block outline OK ({} in frame {})", pos, frames);
			}
			movingFrame = frames;
			movingPos = pos.immutable();
		}
	}

	/**
	 * The test item's model, as Fabric mods write one: after the item model it wraps, it adds a layer whose quads (a
	 * cube's faces) it emits through Fabric's renderer API, which only Fabric's renderer draws, and logs once for each
	 * place the item is shown in (a screen, a hand, the ground, an item frame). Fabric's renderer API gives a mod no
	 * call as its quads are drawn on 26.x (1.21.11's render type getter is gone), so this logs as they are emitted, when
	 * the game updates the item's render state to draw it there.
	 */
	private static final class ItemCheckModel implements ItemModel {
		private static final Map<ItemDisplayContext, String> WHERE = Map.of(ItemDisplayContext.GUI, "screen", ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, "hand",
				ItemDisplayContext.FIRST_PERSON_LEFT_HAND, "hand", ItemDisplayContext.GROUND, "dropped", ItemDisplayContext.FIXED, "frame");
		private static final Map<String, AtomicBoolean> LOGGED = Map.of("screen", new AtomicBoolean(), "hand", new AtomicBoolean(), "dropped", new AtomicBoolean(),
				"frame", new AtomicBoolean());
		private final ItemModel wrapped;

		ItemCheckModel(ItemModel wrapped) {
			this.wrapped = wrapped;
		}

		@Override
		public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext displayContext, @Nullable ClientLevel level,
				@Nullable ItemOwner owner, int seed) {
			wrapped.update(state, stack, resolver, displayContext, level, owner, seed);
			QuadEmitter emitter = ((FabricLayerRenderState) (Object) state.newLayer()).emitter();
			for (Direction face : Direction.values()) emitter.square(face, 0.25F, 0.25F, 0.75F, 0.75F, 0.25F).emit();
			String where = WHERE.get(displayContext);
			if (where != null && !LOGGED.get(where).getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi item {} OK (quads emitted for {})", where, displayContext);
			}
		}
	}

	/**
	 * Glass's model as Continuity left it (R6), wrapped after it: the first time a chunk section draws a glass block next
	 * to another, it logs whether any quad the model emitted is off glass's own sprite, as a connected texture's is
	 * (Continuity's default resource pack gives glass one), and which model it wraps.
	 */
	private static final class ConnectedCheckModel extends WrapperBlockStateModel {
		private static final AtomicBoolean LOGGED = new AtomicBoolean();
		/** How far a texture coordinate may stray outside a sprite and still be on it (float rounding). */
		private static final float EPSILON = 1.0E-5F;

		ConnectedCheckModel(BlockStateModel wrapped) {
			super(wrapped);
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
			if (LOGGED.get() || !isSection(level) || !nextToSame(level, pos, state)) {
				super.emitQuads(emitter, level, pos, state, random, cullTest);
				return;
			}
			TextureAtlasSprite sprite = wrapped.particleMaterial(level, pos, state).sprite();
			int[] quads = new int[2];
			emitter.pushTransform(quad -> {
				quads[0]++;
				if (offSprite(quad, sprite)) quads[1]++;
				return true;
			});
			try {
				super.emitQuads(emitter, level, pos, state, random, cullTest);
			} finally {
				emitter.popTransform();
			}
			if (!LOGGED.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST continuity connected textures {} ({} of {} quads off {} at {}, drawn by {})", quads[1] > 0 ? "OK" : "FAILED",
						quads[1], quads[0], sprite.contents().name(), pos, wrapped.getClass().getName());
			}
		}

		private static boolean nextToSame(BlockAndTintGetter level, BlockPos pos, BlockState state) {
			for (Direction direction : Direction.values()) {
				if (level.getBlockState(pos.relative(direction)).is(state.getBlock())) return true;
			}
			return false;
		}

		private static boolean offSprite(QuadView quad, TextureAtlasSprite sprite) {
			for (int i = 0; i < 4; i++) {
				float u = quad.u(i);
				float v = quad.v(i);
				if (u < sprite.getU0() - EPSILON || u > sprite.getU1() + EPSILON || v < sprite.getV0() - EPSILON || v > sprite.getV1() + EPSILON) return true;
			}
			return false;
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
			if (isSection(level) && !TERRAIN.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi nested terrain OK ({} on {}, seam={})", state, Thread.currentThread().getName(), seam());
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

	/**
	 * A Fabric model that throws the first time it is drawn where {@link #throwsIn} says (a vanilla chunk section, or as an
	 * entity), after emitting the wrapped model's quads and pushing a transform it never pops; it logs once when it is
	 * then asked for its parts (Forge's renderer, or Fabricated drawing them). Under Sodium (its own chunk sections) the
	 * section ones never throw: Sodium's renderer calls them, not Fabricated.
	 */
	private abstract static class ThrowingModel extends WrapperBlockStateModel {
		private final String name;
		private final AtomicBoolean threw = new AtomicBoolean();
		private final AtomicBoolean fellBack = new AtomicBoolean();

		ThrowingModel(BlockStateModel wrapped, String name) {
			super(wrapped);
			this.name = name;
		}

		/** Whether it threw (it throws once). */
		boolean threw() {
			return threw.get();
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
			super.emitQuads(emitter, level, pos, state, random, cullTest);
			if (threw.get() || !throwsIn(level) || threw.getAndSet(true)) return;
			AtomicBoolean leaked = new AtomicBoolean();
			emitter.pushTransform(quad -> {
				if (!leaked.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi leaked transform FAILED (a quad after {} threw reached its transform)", name);
				return true;
			});
			FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi {} threw ({} at {} on {})", name, state, pos, Thread.currentThread().getName());
			throw new IllegalStateException("FABRICATED-TEST: " + name + " throws on purpose (FRAPI rule 8's check)");
		}

		/**
		 * Whether it throws when drawn with this level (the first time only), and only through Fabricated's seam: another
		 * mod's renderer hooks (Forgified Fabric API's) aren't Fabricated's to guard.
		 */
		abstract boolean throwsIn(BlockAndTintGetter level);

		@Override
		public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
			fellBack("vanilla");
			super.collectParts(random, parts);
		}

		/** Once it threw, Fabric's renderer never draws it again: its parts are asked for instead. */
		private void fellBack(String caller) {
			if (threw.get() && !fellBack.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi {} fallback OK ({} collectParts)", name, caller);
		}

		/** Which of the broken models a blockstate names. */
		enum Kind {
			MODEL, PART, ENTITY;

			MapCodec<Unbaked> codec() {
				return switch (this) {
					case MODEL -> BrokenModel.CODEC;
					case PART -> BrokenPartModel.CODEC;
					case ENTITY -> BrokenEntityModel.CODEC;
				};
			}

			static MapCodec<Unbaked> newCodec(Kind kind) {
				return BlockStateModel.Unbaked.CODEC.fieldOf("model").xmap(model -> new Unbaked(model, kind), Unbaked::model);
			}
		}

		/** {@code {"fabric:type": "fabricated_testmod_frapi:broken_check", "...:broken_part_check" or "...:broken_entity_check", "model": <a variant>}}. */
		record Unbaked(BlockStateModel.Unbaked model, Kind kind) implements CustomUnbakedBlockStateModel {
			@Override
			public MapCodec<Unbaked> codec() {
				return kind.codec();
			}

			@Override
			public BlockStateModel bake(ModelBaker baker) {
				BlockStateModel baked = model.bake(baker);
				return switch (kind) {
					case MODEL -> new BrokenModel(baked);
					case PART -> new BrokenPartModel(baked);
					case ENTITY -> new BrokenEntityModel(baked);
				};
			}

			@Override
			public void resolveDependencies(Resolver resolver) {
				model.resolveDependencies(resolver);
			}
		}
	}

	/** The broken test block's model: Fabricated marks its class itself. */
	private static final class BrokenModel extends ThrowingModel {
		static final MapCodec<ThrowingModel.Unbaked> CODEC = Kind.newCodec(Kind.MODEL);

		BrokenModel(BlockStateModel wrapped) {
			super(wrapped, "broken model");
		}

		@Override
		boolean throwsIn(BlockAndTintGetter level) {
			return level instanceof RenderSectionRegion && seam().equals("Fabricated");
		}
	}

	/** A part of the broken nested test block's multipart model: Fabricated finds its class from the stack trace. */
	private static final class BrokenPartModel extends ThrowingModel {
		static final MapCodec<ThrowingModel.Unbaked> CODEC = Kind.newCodec(Kind.PART);

		BrokenPartModel(BlockStateModel wrapped) {
			super(wrapped, "broken part");
		}

		@Override
		boolean throwsIn(BlockAndTintGetter level) {
			return level instanceof RenderSectionRegion && seam().equals("Fabricated");
		}
	}

	/** The model a {@link WrapperCheckModel} wraps on the wrapped broken test block: its throw is its own, not the wrapper's. */
	private static final class BrokenWrappedModel extends ThrowingModel {
		BrokenWrappedModel(BlockStateModel wrapped) {
			super(wrapped, "wrapped model");
		}

		@Override
		boolean throwsIn(BlockAndTintGetter level) {
			return level instanceof RenderSectionRegion && seam().equals("Fabricated");
		}
	}

	/**
	 * A Fabric model wrapping another Fabric model, as Continuity's connected texture models wrap the models they are
	 * given: its own {@code emitQuads} emits what the model it wraps emits, by calling that model's, and its parts are
	 * that model's. On the wrapped broken test block it wraps one that throws (FRAPI rule 8): the throw is put down
	 * to the wrapped model, so this class keeps Fabric's renderer. Logs once when Fabricated calls its emitter for a chunk
	 * section after the model it wraps threw.
	 */
	private static final class WrapperCheckModel extends WrapperBlockStateModel {
		private static final AtomicBoolean KEPT = new AtomicBoolean();
		private final ThrowingModel throwing;

		WrapperCheckModel(ThrowingModel wrapped) {
			super(wrapped);
			this.throwing = wrapped;
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
			boolean afterThrow = throwing.threw();
			throwing.emitQuads(emitter, level, pos, state, random, cullTest);
			if (afterThrow && level instanceof RenderSectionRegion && !KEPT.get() && seam().equals("Fabricated") && !KEPT.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi wrapper kept OK (Fabricated still calls its emitter, at {}, after the model it wraps threw)", pos);
			}
		}
	}

	/** The model of the block the second minecart carries, which throws when drawn as an entity. */
	private static final class BrokenEntityModel extends ThrowingModel {
		static final MapCodec<ThrowingModel.Unbaked> CODEC = Kind.newCodec(Kind.ENTITY);

		BrokenEntityModel(BlockStateModel wrapped) {
			super(wrapped, "broken entity model");
		}

		@Override
		boolean throwsIn(BlockAndTintGetter level) {
			return drawnAsEntity(level) && seam().equals("Fabricated");
		}
	}
}
