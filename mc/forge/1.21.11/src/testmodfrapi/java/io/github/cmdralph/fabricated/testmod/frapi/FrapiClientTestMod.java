package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.render.FabricLayerRenderState;
import net.fabricmc.fabric.api.renderer.v1.render.ItemRenderTypeGetter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric models (their own {@code emitQuads}, and no parts), which Forge's block renderer can't draw, on Forge 61 (as
 * NeoForge's renderer test mod checks them on NeoForge 1.21.11; Forge draws them through upstream's hooks): custom
 * blockstate model types ({@code fabric:type}), one the test block's model, the other the part of the nested test block's
 * vanilla multipart model. Each emits the quads of the vanilla model it wraps, and logs once when a chunk section draws
 * it (vanilla's, or Sodium's), once when it is drawn as a moving block, once with the breaking overlay and once as a
 * minecart's block (a block drawn as an entity), naming the seam that sent it to Fabric's renderer. The test block's
 * model is also asked for its level-aware particle sprite where Forge asks a model with its model data
 * ({@link ParticleSpriteCheck}). And Fabric's level render events at the extraction of the block outline and at the
 * extraction's end are seen to fire: upstream's hooks for them sit in a method Forge 61 gave a parameter (SIG-3).
 *
 * <p>Two more Fabric models throw once a vanilla chunk section draws them, after emitting their quads and pushing a
 * transform they never pop (FRAPI rule 8): one the broken test block's model, the other a part of the broken nested
 * test block's multipart model. Each logs when it is then asked for its parts, and the transform logs a failure if any
 * later quad reaches it. They throw only where Fabricated guards the call ({@link #guarded}), not into another
 * renderer's hooks or a call another renderer makes itself. A third throws inside a Fabric model wrapping it
 * ({@link WrapperCheckModel}), as Continuity's models wrap others: the throw is the wrapped model's, so the wrapper's
 * class keeps Fabric's renderer. A fourth does the same the first time it is drawn as an entity (the block a minecart
 * carries).
 *
 * <p>The test item's model is wrapped after it is baked ({@link ItemCheckModel}): it adds a layer of quads emitted
 * through Fabric's renderer API, and logs once when Fabric's renderer draws them in the hotbar (a screen), in hand, as a
 * dropped item and in an item frame ({@link FrapiTestMod} puts it in each).
 *
 * <p>With Continuity installed, glass's model is wrapped after every other wrapper, Continuity's included
 * ({@link ConnectedCheckModel}), to check that its connected textures reach the chunk section.
 */
public final class FrapiClientTestMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ParticleSpriteCheck.register();
		AtomicBoolean outline = new AtomicBoolean();
		WorldRenderEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register((context, hit) -> {
			if (!outline.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi outline extraction event OK");
		});
		AtomicBoolean extracted = new AtomicBoolean();
		WorldRenderEvents.END_EXTRACTION.register(context -> {
			if (!extracted.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi end extraction event OK");
		});
		CustomUnbakedBlockStateModel.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "terrain_check"), CheckModel.Unbaked.TERRAIN);
		CustomUnbakedBlockStateModel.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "nested_check"), CheckModel.Unbaked.NESTED);
		CustomUnbakedBlockStateModel.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "broken_check"), BrokenModel.CODEC);
		CustomUnbakedBlockStateModel.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "broken_part_check"), BrokenPartModel.CODEC);
		CustomUnbakedBlockStateModel.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "broken_entity_check"), BrokenEntityModel.CODEC);
		ModelLoadingPlugin.register(plugin -> plugin.modifyItemModelAfterBake().register((model, context) ->
				context.itemId().equals(FrapiTestMod.FABRIC_ITEM_ID) ? new ItemCheckModel(model) : model));
		ModelLoadingPlugin.register(plugin -> plugin.modifyBlockModelAfterBake().register((model, context) ->
				BuiltInRegistries.BLOCK.getKey(context.state().getBlock()).equals(FrapiTestMod.WRAPPED_BROKEN_BLOCK_ID)
						? new WrapperCheckModel(new BrokenWrappedModel(model)) : model));
		if (FabricLoader.getInstance().isModLoaded("continuity")) {
			ModelLoadingPlugin.register(plugin -> plugin.modifyBlockModelAfterBake().register(ModelModifier.WRAP_LAST_PHASE, (model, context) ->
					context.state().is(Blocks.GLASS) ? new ConnectedCheckModel(model) : model));
		}
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

	/**
	 * Whether Fabricated's guard ({@code FabricBlockRendering.emit}, FRAPI rule 8) is calling the model: the broken models
	 * throw only then. Under another renderer (Sodium's), which calls the model itself where Fabricated sends a block to
	 * Fabric's renderer, it isn't. Walks the stack: once per broken model.
	 */
	static boolean guarded() {
		return StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getClassName().startsWith("io.github.cmdralph.fabricated.")
				&& frame.getClassName().endsWith(".rendering.FabricBlockRendering") && frame.getMethodName().equals("emit")));
	}

	/** Whether a level is a chunk section's: vanilla's, or Sodium's when Sodium meshes the world. */
	static boolean isSection(BlockAndTintGetter level) {
		return level instanceof RenderSectionRegion || level.getClass().getName().equals("net.caffeinemc.mods.sodium.client.world.LevelSlice");
	}

	/**
	 * What the game is drawing, by the innermost of its frames on the stack that says: {@code "entity"} for a block drawn
	 * as an entity or on its own (the block renderer's method for it, which the block feature renderer calls for a
	 * minecart's block), {@code "breaking"} for the breaking overlay (a method whose name says so), {@code "moving"} for a
	 * moving block (the block feature renderer), or null. The level a caller passes doesn't tell: the overlay's is a
	 * moving block's render state, and a moving block's parts are collected with the client level. This class's own
	 * frames don't count.
	 */
	private static @Nullable String drawing() {
		String self = FrapiClientTestMod.class.getName();
		return StackWalker.getInstance().walk(frames -> frames.filter(frame -> !frame.getClassName().startsWith(self))
				.map(frame -> frame.getMethodName().equals("renderSingleBlock") || frame.getMethodName().equals("renderBlockAsEntity") ? "entity"
						: frame.getMethodName().contains("Breaking") || frame.getMethodName().contains("DestroyAnimation") ? "breaking"
						: frame.getClassName().endsWith(".BlockFeatureRenderer") ? "moving" : null)
				.filter(Objects::nonNull).findFirst().orElse(null));
	}

	/** Whether a block model is being drawn as an entity, or on its own (see {@link #drawing}). Walks the stack: test code only. */
	static boolean drawnAsEntity(BlockAndTintGetter level) {
		return !isSection(level) && "entity".equals(drawing());
	}

	/**
	 * A Fabric model as Fabric mods write one: its geometry is only the quads it emits, those of the model it wraps.
	 * Logs {@code check} the first time a chunk section draws it.
	 */
	private static final class CheckModel implements BlockStateModel, FabricBlockStateModel {
		private static final AtomicBoolean MOVING = new AtomicBoolean();
		private static final AtomicBoolean BREAKING = new AtomicBoolean();
		private static final AtomicBoolean ENTITY = new AtomicBoolean();
		private final BlockStateModel wrapped;
		private final String check;
		private final AtomicBoolean logged = new AtomicBoolean();

		CheckModel(BlockStateModel wrapped, String check) {
			this.wrapped = wrapped;
			this.check = check;
		}

		@Override
		public void collectParts(RandomSource random, List<BlockModelPart> parts) {
			// None: as Fabric mods' models with only emitted quads have.
		}

		@Override
		public TextureAtlasSprite particleIcon() {
			return wrapped.particleIcon();
		}

		@Override
		public TextureAtlasSprite particleSprite(BlockAndTintGetter level, BlockPos pos, BlockState state) {
			if (check.equals(Unbaked.TERRAIN_CHECK)) ParticleSpriteCheck.asked(state, pos);
			return ((FabricBlockStateModel) wrapped).particleSprite(level, pos, state);
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
			// The wrapped vanilla model has no emitQuads of its own: this is upstream's default for it.
			((FabricBlockStateModel) wrapped).emitQuads(emitter, level, pos, state, random, cullTest);
			if (isSection(level)) {
				if (!logged.getAndSet(true)) {
					FrapiTestMod.LOGGER.info("FABRICATED-TEST {} ({} in {} on {}, seam={})", check, state, level.getClass().getName(), Thread.currentThread().getName(), seam());
				}
			} else if (!BREAKING.get() || !MOVING.get() || !ENTITY.get()) {
				String drawing = drawing();
				if ("breaking".equals(drawing) && !BREAKING.getAndSet(true)) {
					FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi breaking OK ({} in {}, seam={})", state, level.getClass().getName(), seam());
				} else if ("moving".equals(drawing) && !MOVING.getAndSet(true)) {
					FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi moving block OK ({} in {}, seam={})", state, level.getClass().getName(), seam());
				} else if ("entity".equals(drawing) && !ENTITY.getAndSet(true)) {
					FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi entity block OK ({} in {}, seam={})", state, level.getClass().getName(), seam());
				}
			}
		}

		/** {@code {"fabric:type": "fabricated_testmod_frapi:terrain_check" or "...:nested_check", "model": <a variant>}}. */
		record Unbaked(BlockStateModel.Unbaked model, String check) implements CustomUnbakedBlockStateModel {
			/** The terrain block's check, whose model {@link ParticleSpriteCheck} asks. */
			static final String TERRAIN_CHECK = "frapi terrain OK";
			static final MapCodec<Unbaked> TERRAIN = codec(TERRAIN_CHECK);
			static final MapCodec<Unbaked> NESTED = codec("frapi nested terrain OK");

			private static MapCodec<Unbaked> codec(String check) {
				return BlockStateModel.Unbaked.CODEC.fieldOf("model").xmap(model -> new Unbaked(model, check), Unbaked::model);
			}

			@Override
			public MapCodec<Unbaked> codec() {
				return check.equals(TERRAIN_CHECK) ? TERRAIN : NESTED;
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

	/**
	 * Glass's model as Continuity left it (R6), wrapped after it: the first time a chunk section draws a glass block next
	 * to another, it logs whether any quad the model emitted is off glass's own sprite, as a connected texture's is
	 * (Continuity's default resource pack gives glass one), and which model it wraps. Its parts are the wrapped model's.
	 */
	private static final class ConnectedCheckModel implements BlockStateModel, FabricBlockStateModel {
		private static final AtomicBoolean LOGGED = new AtomicBoolean();
		/** How far a texture coordinate may stray outside a sprite and still be on it (float rounding). */
		private static final float EPSILON = 1.0E-5F;
		private final BlockStateModel wrapped;

		ConnectedCheckModel(BlockStateModel wrapped) {
			this.wrapped = wrapped;
		}

		@Override
		public void collectParts(RandomSource random, List<BlockModelPart> parts) {
			wrapped.collectParts(random, parts);
		}

		@Override
		public TextureAtlasSprite particleIcon() {
			return wrapped.particleIcon();
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
			FabricBlockStateModel fabricModel = (FabricBlockStateModel) wrapped;
			if (LOGGED.get() || !isSection(level) || !nextToSame(level, pos, state)) {
				fabricModel.emitQuads(emitter, level, pos, state, random, cullTest);
				return;
			}
			TextureAtlasSprite sprite = wrapped.particleIcon();
			int[] quads = new int[2];
			emitter.pushTransform(quad -> {
				quads[0]++;
				if (offSprite(quad, sprite)) quads[1]++;
				return true;
			});
			try {
				fabricModel.emitQuads(emitter, level, pos, state, random, cullTest);
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

	/**
	 * The test item's model, as Fabric mods write one: after the item model it wraps, it adds a layer whose quads (a
	 * cube's faces) it emits through Fabric's renderer API, which only Fabric's renderer draws. For each place the item
	 * is shown in (a screen, a hand, the ground, an item frame) the layer gets a render type getter Fabric's renderer
	 * asks as it draws those quads, which logs once and keeps the layer's render type.
	 */
	private static final class ItemCheckModel implements ItemModel {
		private static final Map<ItemDisplayContext, ItemRenderTypeGetter> GETTERS = getters();
		private final ItemModel wrapped;

		ItemCheckModel(ItemModel wrapped) {
			this.wrapped = wrapped;
		}

		@Override
		public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext displayContext, @Nullable ClientLevel level,
				@Nullable ItemOwner owner, int seed) {
			wrapped.update(state, stack, resolver, displayContext, level, owner, seed);
			ItemStackRenderState.LayerRenderState layer = state.newLayer();
			layer.setRenderType(Sheets.cutoutBlockSheet());
			FabricLayerRenderState fabricLayer = (FabricLayerRenderState) (Object) layer;
			ItemRenderTypeGetter getter = GETTERS.get(displayContext);
			if (getter != null) fabricLayer.setRenderTypeGetter(getter);
			QuadEmitter emitter = fabricLayer.emitter();
			for (Direction face : Direction.values()) emitter.square(face, 0.25F, 0.25F, 0.75F, 0.75F, 0.25F).emit();
		}

		private static Map<ItemDisplayContext, ItemRenderTypeGetter> getters() {
			Map<ItemDisplayContext, String> where = Map.of(ItemDisplayContext.GUI, "screen", ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, "hand",
					ItemDisplayContext.FIRST_PERSON_LEFT_HAND, "hand", ItemDisplayContext.GROUND, "dropped", ItemDisplayContext.FIXED, "frame");
			Map<String, AtomicBoolean> logged = new HashMap<>();
			Map<ItemDisplayContext, ItemRenderTypeGetter> getters = new EnumMap<>(ItemDisplayContext.class);
			where.forEach((displayContext, name) -> {
				AtomicBoolean once = logged.computeIfAbsent(name, key -> new AtomicBoolean());
				getters.put(displayContext, (atlas, sectionLayer) -> {
					if (!once.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi item {} OK (its emitted quads drawn {}, {} atlas)", name, displayContext, atlas);
					return null;
				});
			});
			return getters;
		}
	}

	/**
	 * A Fabric model that throws the first time it is drawn where {@link #throwsIn} says (a vanilla chunk section, or as an
	 * entity), after emitting the wrapped model's quads and pushing a transform it never pops; it logs once when it is
	 * then asked for its parts (its vanilla method). Under Sodium (its own chunk sections) the section ones never throw.
	 */
	private abstract static class ThrowingModel implements BlockStateModel, FabricBlockStateModel {
		private final BlockStateModel wrapped;
		private final String name;
		private final AtomicBoolean threw = new AtomicBoolean();
		private final AtomicBoolean fellBack = new AtomicBoolean();

		ThrowingModel(BlockStateModel wrapped, String name) {
			this.wrapped = wrapped;
			this.name = name;
		}

		/** Whether it threw (it throws once). */
		boolean threw() {
			return threw.get();
		}

		@Override
		public void collectParts(RandomSource random, List<BlockModelPart> parts) {
			if (threw.get() && !fellBack.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi {} fallback OK", name);
			wrapped.collectParts(random, parts);
		}

		@Override
		public TextureAtlasSprite particleIcon() {
			return wrapped.particleIcon();
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
			((FabricBlockStateModel) wrapped).emitQuads(emitter, level, pos, state, random, cullTest);
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
			return level instanceof RenderSectionRegion && guarded();
		}
	}

	/** The model a {@link WrapperCheckModel} wraps on the wrapped broken test block: its throw is its own, not the wrapper's. */
	private static final class BrokenWrappedModel extends ThrowingModel {
		BrokenWrappedModel(BlockStateModel wrapped) {
			super(wrapped, "wrapped model");
		}

		@Override
		boolean throwsIn(BlockAndTintGetter level) {
			return level instanceof RenderSectionRegion && guarded();
		}
	}

	/**
	 * A Fabric model wrapping another Fabric model, as Continuity's connected texture models wrap the models they are
	 * given: its own {@code emitQuads} emits what the model it wraps emits, by calling that model's, and its parts are
	 * that model's. On the wrapped broken test block it wraps one that throws (FRAPI rule 8): the throw is put down to the
	 * wrapped model, so this class keeps Fabric's renderer. Logs once when Fabricated calls its emitter for a chunk
	 * section after the model it wraps threw.
	 */
	private static final class WrapperCheckModel implements BlockStateModel, FabricBlockStateModel {
		private static final AtomicBoolean KEPT = new AtomicBoolean();
		private final ThrowingModel wrapped;

		WrapperCheckModel(ThrowingModel wrapped) {
			this.wrapped = wrapped;
		}

		@Override
		public void collectParts(RandomSource random, List<BlockModelPart> parts) {
			wrapped.collectParts(random, parts);
		}

		@Override
		public TextureAtlasSprite particleIcon() {
			return wrapped.particleIcon();
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
			boolean afterThrow = wrapped.threw();
			wrapped.emitQuads(emitter, level, pos, state, random, cullTest);
			if (afterThrow && level instanceof RenderSectionRegion && !KEPT.get() && seam().equals("Fabricated") && !KEPT.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi wrapper kept OK (Fabricated still calls its emitter, at {}, after the model it wraps threw)", pos);
			}
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
			return level instanceof RenderSectionRegion && guarded();
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
			return drawnAsEntity(level) && guarded();
		}
	}
}
