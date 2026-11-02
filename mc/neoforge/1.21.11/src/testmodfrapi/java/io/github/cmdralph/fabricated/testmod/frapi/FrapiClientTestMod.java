package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.ArrayList;
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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadTransform;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.render.FabricLayerRenderState;
import net.fabricmc.fabric.api.renderer.v1.render.ItemRenderTypeGetter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
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
import net.minecraft.world.level.EmptyBlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;

/**
 * Fabric models (their own {@code emitQuads}, and no parts), which NeoForge's block renderer can't draw: custom
 * blockstate model types ({@code fabric:type}), one the test block's model, the other the part of the nested test block's
 * vanilla multipart model. Each emits the quads of a NeoForge model with no {@code emitQuads} of its own, drawn through
 * Fabricated's NeoForge-aware default, and logs once when a chunk section draws it (vanilla's, or Sodium's), once when it
 * is drawn as a moving block and once with the breaking overlay. Once a world is loaded, the parts NeoForge's renderers
 * get from the test block's model are checked to be made from its quads, with NeoForge's baked colour kept (1.21.11).
 *
 * <p>Two more Fabric models throw once a vanilla chunk section draws them, after emitting their quads and pushing a
 * transform they never pop (FRAPI rule 8): one the broken test block's model, the other a part of the broken nested
 * test block's multipart model. Each logs when NeoForge's renderer then asks it for its parts, and the transform logs a
 * failure if any later quad reaches it.
 *
 * <p>The test item's model is wrapped after it is baked ({@link ItemCheckModel}): it adds a layer of quads emitted
 * through Fabric's renderer API, and logs once when Fabric's renderer draws them in the hotbar (a screen), in hand, as a
 * dropped item and in an item frame ({@link FrapiTestMod} puts it in each).
 */
public final class FrapiClientTestMod implements ClientModInitializer {
	/**
	 * The colour the terrain block's model gives its faces ({@code neoforge_data}), as NeoForge bakes it into its quads
	 * (ARGB, as Fabric's quads have it): red and blue differ, so channels in the wrong order fail the check.
	 */
	private static final int BAKED_COLOR = 0xFF8040C0;
	private static final AtomicBoolean COLORS = new AtomicBoolean();
	/** Logs once that a quad reached Fabric's renderer with NeoForge's baked colour. */
	private static final QuadTransform CHECK_COLORS = quad -> {
		if (quad.color(0) == BAKED_COLOR && !COLORS.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi baked colors OK");
		return true;
	};
	// Client thread only.
	private static boolean partsChecked;

	@Override
	public void onInitializeClient() {
		CustomUnbakedBlockStateModel.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "terrain_check"), CheckModel.Unbaked.TERRAIN);
		CustomUnbakedBlockStateModel.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "nested_check"), CheckModel.Unbaked.NESTED);
		CustomUnbakedBlockStateModel.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "broken_check"), BrokenModel.CODEC);
		CustomUnbakedBlockStateModel.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "broken_part_check"), BrokenPartModel.CODEC);
		ModelLoadingPlugin.register(plugin -> plugin.modifyItemModelAfterBake().register((model, context) ->
				context.itemId().equals(FrapiTestMod.FABRIC_ITEM_ID) ? new ItemCheckModel(model) : model));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != null && !partsChecked) {
				partsChecked = true;
				checkNeoForgeParts(client);
			}
		});
	}

	/** Whether a level is a chunk section's: vanilla's, or Sodium's when Sodium meshes the world. */
	static boolean isSection(BlockAndTintGetter level) {
		return level instanceof RenderSectionRegion || level.getClass().getName().equals("net.caffeinemc.mods.sodium.client.world.LevelSlice");
	}

	/**
	 * What NeoForge's renderers, and renderer mods drawing blocks NeoForge's way, get from the test block's Fabric model:
	 * parts made from the quads it emits, the wrapped model's baked colour among them.
	 */
	private static void checkNeoForgeParts(Minecraft client) {
		BlockState state = BuiltInRegistries.BLOCK.getValue(FrapiTestMod.TERRAIN_BLOCK_ID).defaultBlockState();
		List<BlockModelPart> parts = client.getBlockRenderer().getBlockModel(state).collectParts(EmptyBlockAndTintGetter.INSTANCE, BlockPos.ZERO, state,
				RandomSource.create(42L));
		List<BakedQuad> quads = new ArrayList<>();
		for (BlockModelPart part : parts) {
			quads.addAll(part.getQuads(null));
			for (Direction direction : Direction.values()) quads.addAll(part.getQuads(direction));
		}
		boolean colored = quads.stream().anyMatch(quad -> quad.bakedColors().color(0) == BAKED_COLOR);
		if (colored) {
			FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi NeoForge parts OK ({} parts, {} quads)", parts.size(), quads.size());
		} else {
			FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi NeoForge parts FAILED ({} parts, {} quads, none with the baked colour)", parts.size(), quads.size());
		}
	}

	/**
	 * What the game is drawing, by the innermost of its frames on the stack that says: {@code "breaking"} for the breaking
	 * overlay (a method whose name says so), {@code "moving"} for a moving block (the block feature renderer), or null.
	 * The level a caller passes doesn't tell: the overlay's is a moving block's render state, and a moving block's parts
	 * are collected with the client level. This class's own frames don't count.
	 */
	private static @Nullable String drawing() {
		String self = FrapiClientTestMod.class.getName();
		return StackWalker.getInstance().walk(frames -> frames.filter(frame -> !frame.getClassName().startsWith(self))
				.map(frame -> frame.getMethodName().contains("Breaking") || frame.getMethodName().contains("DestroyAnimation") ? "breaking"
						: frame.getClassName().endsWith(".BlockFeatureRenderer") ? "moving" : null)
				.filter(Objects::nonNull).findFirst().orElse(null));
	}

	/**
	 * A Fabric model as Fabric mods write one: its geometry is only the quads it emits, those of the model it wraps.
	 * Logs {@code check} the first time a chunk section draws it.
	 */
	private static final class CheckModel implements BlockStateModel, FabricBlockStateModel {
		private static final AtomicBoolean MOVING = new AtomicBoolean();
		private static final AtomicBoolean BREAKING = new AtomicBoolean();
		private final BlockStateModel wrapped;
		private final String check;
		private final AtomicBoolean logged = new AtomicBoolean();

		CheckModel(BlockStateModel wrapped, String check) {
			this.wrapped = wrapped;
			this.check = check;
		}

		@Override
		public void collectParts(RandomSource random, List<BlockModelPart> parts) {
			// None: a renderer drawing blocks NeoForge's way gets the parts Fabricated makes from the emitted quads.
		}

		@Override
		public TextureAtlasSprite particleIcon() {
			return wrapped.particleIcon();
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
			emitter.pushTransform(CHECK_COLORS);
			try {
				// The wrapped NeoForge model has no emitQuads of its own: this is Fabricated's default for it.
				((FabricBlockStateModel) wrapped).emitQuads(emitter, level, pos, state, random, cullTest);
			} finally {
				emitter.popTransform();
			}
			if (isSection(level)) {
				if (!logged.getAndSet(true)) {
					FrapiTestMod.LOGGER.info("FABRICATED-TEST {} ({} in {} on {})", check, state, level.getClass().getName(), Thread.currentThread().getName());
				}
			} else if (!BREAKING.get() || !MOVING.get()) {
				String drawing = drawing();
				if ("breaking".equals(drawing) && !BREAKING.getAndSet(true)) {
					FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi breaking OK ({} in {})", state, level.getClass().getName());
				} else if ("moving".equals(drawing) && !MOVING.getAndSet(true)) {
					FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi moving block OK ({} in {})", state, level.getClass().getName());
				}
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
				return new CheckModel(new LevelAwareModel(model.bake(baker)), check);
			}

			@Override
			public void resolveDependencies(Resolver resolver) {
				model.resolveDependencies(resolver);
			}
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

	/** A NeoForge model with no {@code emitQuads} of its own, whose parts depend on the level. */
	private static final class LevelAwareModel extends DelegateBlockStateModel {
		private static final AtomicBoolean TERRAIN = new AtomicBoolean();

		LevelAwareModel(BlockStateModel wrapped) {
			super(wrapped);
		}

		@Override
		public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockModelPart> parts) {
			if (isSection(level) && !TERRAIN.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi level-aware default OK ({})", state);
			super.collectParts(level, pos, state, random, parts);
		}
	}

	/**
	 * A Fabric model that throws the first time a vanilla chunk section draws it, after emitting the wrapped model's quads
	 * and pushing a transform it never pops; it logs once when NeoForge's renderer then asks it for its parts (its
	 * vanilla method, which Fabricated's default calls once Fabric's renderer no longer draws it). Under Sodium (its own
	 * chunk sections) it never throws.
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
			if (!(level instanceof RenderSectionRegion) || threw.getAndSet(true)) return;
			AtomicBoolean leaked = new AtomicBoolean();
			emitter.pushTransform(quad -> {
				if (!leaked.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi leaked transform FAILED (a quad after {} threw reached its transform)", name);
				return true;
			});
			FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi {} threw ({} at {} on {})", name, state, pos, Thread.currentThread().getName());
			throw new IllegalStateException("FABRICATED-TEST: " + name + " throws on purpose (FRAPI rule 8's check)");
		}

		/** {@code {"fabric:type": "fabricated_testmod_frapi:broken_check" or "...:broken_part_check", "model": <a variant>}}. */
		record Unbaked(BlockStateModel.Unbaked model, boolean part) implements CustomUnbakedBlockStateModel {
			@Override
			public MapCodec<Unbaked> codec() {
				return part ? BrokenPartModel.CODEC : BrokenModel.CODEC;
			}

			@Override
			public BlockStateModel bake(ModelBaker baker) {
				return part ? new BrokenPartModel(model.bake(baker)) : new BrokenModel(model.bake(baker));
			}

			@Override
			public void resolveDependencies(Resolver resolver) {
				model.resolveDependencies(resolver);
			}
		}
	}

	/** The broken test block's model: Fabricated marks its class itself. */
	private static final class BrokenModel extends ThrowingModel {
		static final MapCodec<ThrowingModel.Unbaked> CODEC = BlockStateModel.Unbaked.CODEC.fieldOf("model").xmap(model -> new ThrowingModel.Unbaked(model, false),
				ThrowingModel.Unbaked::model);

		BrokenModel(BlockStateModel wrapped) {
			super(wrapped, "broken model");
		}
	}

	/** A part of the broken nested test block's multipart model: Fabricated finds its class from the stack trace. */
	private static final class BrokenPartModel extends ThrowingModel {
		static final MapCodec<ThrowingModel.Unbaked> CODEC = BlockStateModel.Unbaked.CODEC.fieldOf("model").xmap(model -> new ThrowingModel.Unbaked(model, true),
				ThrowingModel.Unbaked::model);

		BrokenPartModel(BlockStateModel wrapped) {
			super(wrapped, "broken part");
		}
	}
}
