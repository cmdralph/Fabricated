package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * fabric-rendering-v1 on 1.21.1: color providers for the test block and its item, registered from the client
 * entrypoint as Fabric mods register theirs, must be the game's colors once it has started, and Fabric's lookups
 * ({@code ColorProviderRegistry.get}, which reads NeoForge's maps by block and by item) must return them. A HUD
 * callback must be called as the HUD is drawn.
 *
 * <p>fabric-key-binding-api-v1 (KEYMAP-2): a key binding registered from the client entrypoint must be in the game's
 * options once it has started, its category sorted after the game's, registering it twice or once the options exist
 * must throw as upstream's does, and by the time the game stops it must have the key CI's options file saved for it
 * ({@code ci/neoforge/smoke-client.sh}).
 *
 * <p>fabric-renderer-api-v1 (FRAPI-5): Fabric models (no vanilla adapter: only the quads they emit, none for vanilla's
 * renderer), put in place of the test blocks' models after they are baked (fabric-model-loading-api-v1), one alone, one
 * as the part of a vanilla multipart model. Each emits the quads of the vanilla model it wraps, and logs once when a
 * chunk section draws it, once when it is drawn as a falling block, once as a block a piston moves, and once with the
 * breaking overlay.
 *
 * <p>Two more Fabric models throw once a chunk section draws them, after emitting their quads and pushing a transform
 * they never pop (FRAPI rule 8): one the broken test block's model, the other a part of the broken nested test block's
 * multipart model. Each logs when NeoForge's renderer then asks it for its quads, and the transform logs a failure if
 * any later quad reaches it.
 *
 * <p>The test item's model is a Fabric model too ({@link ItemCheckModel}): it emits the vanilla item model's quads, and
 * logs once when it is drawn in the hotbar (a screen), in hand, as a dropped item and in an item frame
 * ({@link FrapiTestMod} puts it in each).
 */
public final class FrapiClientTestMod implements ClientModInitializer {
	private static final int BLOCK_COLOR = 0x3366CC;
	private static final int ITEM_COLOR = 0xCC6633;
	private static final BlockColor BLOCK_PROVIDER = (state, level, pos, tintIndex) -> BLOCK_COLOR;
	private static final ItemColor ITEM_PROVIDER = (stack, tintIndex) -> ITEM_COLOR;
	private static final KeyMapping KEY = new KeyMapping("key.fabricated_testmod_frapi.check", InputConstants.Type.KEYSYM, InputConstants.KEY_J,
			"key.categories.fabricated_testmod_frapi");
	// Render thread only.
	private static boolean hudDrawn;
	private static String keyFailure;

	@Override
	public void onInitializeClient() {
		if (KeyBindingHelper.registerKeyBinding(KEY) != KEY) keyFailure = "registering didn't return the key binding";
		try {
			KeyBindingHelper.registerKeyBinding(KEY);
			keyFailure = "registering it twice didn't throw";
		} catch (IllegalArgumentException expected) {
			// As upstream.
		}
		ColorProviderRegistry.BLOCK.register(BLOCK_PROVIDER, FrapiTestMod.tintedBlock);
		ColorProviderRegistry.ITEM.register(ITEM_PROVIDER, FrapiTestMod.tintedItem);
		HudRenderCallback.EVENT.register((graphics, delta) -> {
			if (!hudDrawn) {
				hudDrawn = true;
				FrapiTestMod.LOGGER.info("FABRICATED-TEST hud callback OK");
			}
		});
		ModelLoadingPlugin.register(context -> context.modifyModelAfterBake().register(FrapiClientTestMod::fabricModels));
		ClientLifecycleEvents.CLIENT_STARTED.register(FrapiClientTestMod::checkColors);
		ClientLifecycleEvents.CLIENT_STARTED.register(FrapiClientTestMod::checkKeyBinding);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			InputConstants.Key saved = InputConstants.getKey("key.keyboard.k");
			InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(KEY);
			if (saved.equals(bound)) FrapiTestMod.LOGGER.info("FABRICATED-TEST key binding saved key OK");
			else FrapiTestMod.LOGGER.error("FABRICATED-TEST key binding saved key FAILED: bound to {}, not the saved {}", bound.getName(), saved.getName());
		});
	}

	private static void checkKeyBinding(Minecraft client) {
		List<String> failed = new ArrayList<>();
		if (keyFailure != null) failed.add(keyFailure);
		KeyMapping[] all = client.options.keyMappings;
		if (!Arrays.asList(all).contains(KEY)) failed.add("the game's options don't list it");
		try {
			Arrays.sort(all.clone());
			if (KEY.compareTo(client.options.keyUp) <= 0) failed.add("its category doesn't sort after the game's");
		} catch (RuntimeException e) {
			failed.add("the controls screen's sort threw " + e);
		}
		try {
			// Upstream checks for the options first, so this is refused as late, not as a duplicate.
			KeyBindingHelper.registerKeyBinding(KEY);
			failed.add("registering once the options exist didn't throw");
		} catch (IllegalStateException expected) {
			// As upstream.
		} catch (IllegalArgumentException e) {
			failed.add("registering once the options exist was refused as a duplicate, not as late");
		}
		if (failed.isEmpty()) FrapiTestMod.LOGGER.info("FABRICATED-TEST key binding OK");
		else FrapiTestMod.LOGGER.error("FABRICATED-TEST key binding FAILED: {}", String.join("; ", failed));
	}

	private static void checkColors(Minecraft client) {
		List<String> failed = new ArrayList<>();
		if (ColorProviderRegistry.BLOCK.get(FrapiTestMod.tintedBlock) != BLOCK_PROVIDER) failed.add("Fabric's block lookup didn't return the provider");
		int block = client.getBlockColors().getColor(FrapiTestMod.tintedBlock.defaultBlockState(), null, null, 0);
		if (block != BLOCK_COLOR) failed.add("the block's color is " + Integer.toHexString(block));
		if (ColorProviderRegistry.ITEM.get(FrapiTestMod.tintedItem) != ITEM_PROVIDER) failed.add("Fabric's item lookup didn't return the provider");
		int item = client.getItemColors().getColor(new ItemStack(FrapiTestMod.tintedItem), 0);
		if (item != ITEM_COLOR) failed.add("the item's color is " + Integer.toHexString(item));
		if (failed.isEmpty()) FrapiTestMod.LOGGER.info("FABRICATED-TEST colors OK");
		else FrapiTestMod.LOGGER.error("FABRICATED-TEST colors FAILED: {}", String.join("; ", failed));
	}

	/**
	 * Puts the Fabric models in place of the test blocks' baked models (their block states', a top-level model each) and
	 * of the test item's (its inventory model, top-level too).
	 */
	private static @Nullable BakedModel fabricModels(@Nullable BakedModel model, ModelModifier.AfterBake.Context context) {
		ModelResourceLocation id = context.topLevelId();
		if (model == null || id == null || !id.id().getNamespace().equals(FrapiTestMod.NAMESPACE)) return model;
		if (id.variant().equals("inventory")) return id.id().getPath().equals("fabric_item") ? new ItemCheckModel(model) : model;
		if (!id.variant().isEmpty()) return model;
		return switch (id.id().getPath()) {
			case "terrain_block" -> {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi model plugin OK ({} after it was baked)", id);
				yield new CheckModel(model, "frapi terrain OK");
			}
			case "nested_block" -> new MultiPartBakedModel(List.<Pair<Predicate<BlockState>, BakedModel>>of(Pair.of(state -> true,
					new CheckModel(model, "frapi nested terrain OK"))));
			case "broken_block" -> new BrokenModel(model);
			case "broken_nested_block" -> new MultiPartBakedModel(List.<Pair<Predicate<BlockState>, BakedModel>>of(Pair.of(state -> true, model),
					Pair.of(state -> true, new BrokenPartModel(model))));
			default -> model;
		};
	}

	/**
	 * What the game is drawing, by the innermost of its frames on the stack that says: {@code "breaking"} for the breaking
	 * overlay, {@code "piston"} for a block a piston moves, {@code "falling"} for a falling block, or null. This class's
	 * own frames don't count.
	 */
	private static @Nullable String drawing() {
		String self = FrapiClientTestMod.class.getName();
		return StackWalker.getInstance().walk(frames -> frames.filter(frame -> !frame.getClassName().startsWith(self))
				.map(frame -> frame.getMethodName().contains("Breaking") ? "breaking"
						: frame.getMethodName().equals("renderPistonMovedBlocks") || frame.getClassName().endsWith(".PistonHeadRenderer") ? "piston"
						: frame.getClassName().endsWith(".FallingBlockRenderer") ? "falling" : null)
				.filter(Objects::nonNull).findFirst().orElse(null));
	}

	/** A model as Fabric mods write one for Fabric's renderer API on 1.21.1, around the vanilla model whose quads it emits. */
	private abstract static class FabricModel implements BakedModel, FabricBakedModel {
		final BakedModel wrapped;

		FabricModel(BakedModel wrapped) {
			this.wrapped = wrapped;
		}

		@Override
		public boolean isVanillaAdapter() {
			return false;
		}

		@Override
		public boolean useAmbientOcclusion() {
			return wrapped.useAmbientOcclusion();
		}

		@Override
		public boolean isGui3d() {
			return wrapped.isGui3d();
		}

		@Override
		public boolean usesBlockLight() {
			return wrapped.usesBlockLight();
		}

		@Override
		public boolean isCustomRenderer() {
			return false;
		}

		@Override
		public TextureAtlasSprite getParticleIcon() {
			return wrapped.getParticleIcon();
		}

		@Override
		public ItemTransforms getTransforms() {
			return wrapped.getTransforms();
		}

		@Override
		public ItemOverrides getOverrides() {
			return wrapped.getOverrides();
		}

		/** Emits the wrapped vanilla model's quads (its default emitter, Fabric's renderer API's). */
		void emitWrapped(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier, RenderContext context) {
			((FabricBakedModel) wrapped).emitBlockQuads(level, state, pos, randomSupplier, context);
		}
	}

	/** Its geometry is only the quads it emits: none for vanilla's renderer. Logs {@code check} the first time a chunk section draws it. */
	private static final class CheckModel extends FabricModel {
		private static final AtomicBoolean FALLING = new AtomicBoolean();
		private static final AtomicBoolean PISTON = new AtomicBoolean();
		private static final AtomicBoolean BREAKING = new AtomicBoolean();
		private final String check;
		private final AtomicBoolean logged = new AtomicBoolean();

		CheckModel(BakedModel wrapped, String check) {
			super(wrapped);
			this.check = check;
		}

		@Override
		public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
			return List.of();
		}

		@Override
		public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier, RenderContext context) {
			emitWrapped(level, state, pos, randomSupplier, context);
			if (level instanceof RenderChunkRegion) {
				if (!logged.getAndSet(true)) {
					FrapiTestMod.LOGGER.info("FABRICATED-TEST {} ({} in {} on {})", check, state, level.getClass().getName(), Thread.currentThread().getName());
				}
				return;
			}
			if (FALLING.get() && PISTON.get() && BREAKING.get()) return;
			String drawing = drawing();
			if ("breaking".equals(drawing) && !BREAKING.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi breaking OK ({} in {})", state, level.getClass().getName());
			} else if ("piston".equals(drawing) && !PISTON.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi piston OK ({} in {})", state, level.getClass().getName());
			} else if ("falling".equals(drawing) && !FALLING.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi moving block OK ({} in {})", state, level.getClass().getName());
			}
		}
	}

	/**
	 * The test item's model: its geometry is only the quads it emits (the vanilla item model's), none for vanilla's
	 * renderer. Logs once for each place it is drawn in: a screen (the hotbar), a hand, the ground, an item frame.
	 */
	private static final class ItemCheckModel extends FabricModel {
		private static final Map<ItemDisplayContext, String> WHERE = Map.of(ItemDisplayContext.GUI, "screen", ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, "hand",
				ItemDisplayContext.FIRST_PERSON_LEFT_HAND, "hand", ItemDisplayContext.GROUND, "dropped", ItemDisplayContext.FIXED, "frame");
		private static final Map<String, AtomicBoolean> LOGGED = Map.of("screen", new AtomicBoolean(), "hand", new AtomicBoolean(), "dropped", new AtomicBoolean(),
				"frame", new AtomicBoolean());

		ItemCheckModel(BakedModel wrapped) {
			super(wrapped);
		}

		@Override
		public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
			return List.of();
		}

		@Override
		public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
			// The wrapped vanilla model's default emitter: Fabric's renderer API's.
			((FabricBakedModel) wrapped).emitItemQuads(stack, randomSupplier, context);
			ItemDisplayContext displayContext = context.itemTransformationMode();
			String where = WHERE.get(displayContext);
			if (where != null && !LOGGED.get(where).getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi item {} OK ({} drawn {})", where, stack, displayContext);
			}
		}
	}

	/**
	 * A Fabric model that throws the first time a chunk section draws it, after emitting the wrapped model's quads and
	 * pushing a transform it never pops. Its vanilla quads are the wrapped model's: it logs once when NeoForge's renderer
	 * then asks for them.
	 */
	private abstract static class ThrowingModel extends FabricModel {
		private final String name;
		private final AtomicBoolean threw = new AtomicBoolean();
		private final AtomicBoolean fellBack = new AtomicBoolean();

		ThrowingModel(BakedModel wrapped, String name) {
			super(wrapped);
			this.name = name;
		}

		@Override
		public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
			if (threw.get() && !fellBack.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi {} fallback OK", name);
			return wrapped.getQuads(state, direction, random);
		}

		@Override
		public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier, RenderContext context) {
			emitWrapped(level, state, pos, randomSupplier, context);
			if (!(level instanceof RenderChunkRegion) || threw.getAndSet(true)) return;
			AtomicBoolean leaked = new AtomicBoolean();
			context.pushTransform(quad -> {
				if (!leaked.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi leaked transform FAILED (a quad after {} threw reached its transform)", name);
				return true;
			});
			FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi {} threw ({} at {} on {})", name, state, pos, Thread.currentThread().getName());
			throw new IllegalStateException("FABRICATED-TEST: " + name + " throws on purpose (FRAPI rule 8's check)");
		}
	}

	/** The broken test block's model: Fabricated marks its class itself. */
	private static final class BrokenModel extends ThrowingModel {
		BrokenModel(BakedModel wrapped) {
			super(wrapped, "broken model");
		}
	}

	/** A part of the broken nested test block's multipart model: Fabricated finds its class from the stack trace. */
	private static final class BrokenPartModel extends ThrowingModel {
		BrokenPartModel(BakedModel wrapped) {
			super(wrapped, "broken part");
		}
	}
}
