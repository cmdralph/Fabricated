package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
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
 * fabric-renderer-api-v1 on Forge 47 (task 4, R5; as FRAPI-5 on NeoForge 21.1): Fabric models (no vanilla adapter:
 * only the quads they emit, none for Forge's renderer), put in place of the test blocks' models after they are baked
 * (fabric-model-loading-api-v1), one alone, one as the part of a vanilla multipart model. Each emits the quads of the
 * vanilla model it wraps, and logs once when a chunk section draws it, once when it is drawn as a falling block, once as
 * a block a piston moves, and once with the breaking overlay, naming the seam that sent it to Fabric's renderer: on
 * Forge 47 Fabricated's, as upstream's hooks miss Forge's code there.
 *
 * <p>Two more Fabric models throw once a chunk section draws them through Fabricated's seam, after emitting their quads
 * and pushing a transform they never pop (FRAPI rule 8): one the broken test block's model, the other a part of the
 * broken nested test block's multipart model. Each logs when Forge's renderer then asks it for its quads, and the
 * transform logs a failure if any later quad reaches it. A third does the same inside a Fabric model wrapping it
 * ({@link WrapperCheckModel}), as Continuity's models wrap others: the throw is the wrapped model's, so the wrapper's
 * class keeps Fabric's renderer.
 *
 * <p>The test item's model is a Fabric model too ({@link ItemCheckModel}): it emits the vanilla item model's quads, and
 * logs once when it is drawn in the hotbar (a screen), in hand, as a dropped item and in an item frame
 * ({@link FrapiTestMod} puts it in each), which Indigo's own item hook draws on Forge 47.
 */
public final class FrapiClientTestMod implements ClientModInitializer {
	/** The class through which Fabricated sends a block to Fabric's renderer on Forge 47. */
	private static final String FABRICATED_SEAM = "io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering";
	/** Half a minute of client ticks, which a slow integrated server's first seconds in the world fit in. */
	private static final int MAX_HOLD = 600;
	/** Client ticks held so far (render thread). */
	private static int held;

	@Override
	public void onInitializeClient() {
		ModelLoadingPlugin.register(context -> context.modifyModelAfterBake().register(FrapiClientTestMod::fabricModels));
	}

	/**
	 * On the client, each tick: whether mc-runtime-test, which CI's client runs start the game with and which quits a few
	 * seconds after the player's chunk has loaded, is to wait that tick ({@code mixin/McRuntimeTestHoldMixin}). It waits
	 * once the test blocks are placed until the first test block has been drawn as a falling block, as a block a piston
	 * moves and with the breaking overlay, and the test item in each place it is put, for half a minute at most: a server
	 * running seconds behind had moved the piston too few times when the game quit, and no moved block was drawn
	 * (extended run 41642906615); a game that quit once the moved blocks were drawn hadn't yet drawn the dropped item,
	 * which the server spawned with the item frame (PR run 41766936311).
	 */
	public static boolean holding() {
		return FrapiTestMod.placed() && !(CheckModel.movingDrawn() && ItemCheckModel.allDrawn()) && held++ < MAX_HOLD;
	}

	/**
	 * Puts the Fabric models in place of the test blocks' baked models (their block states', a top-level model each, with
	 * a {@code ModelResourceLocation} as their id) and of the test item's (its inventory model, top-level too).
	 */
	private static @Nullable BakedModel fabricModels(@Nullable BakedModel model, ModelModifier.AfterBake.Context context) {
		if (model == null || !(context.id() instanceof ModelResourceLocation id) || !id.getNamespace().equals(FrapiTestMod.NAMESPACE)) return model;
		if (id.getVariant().equals("inventory")) return id.getPath().equals("fabric_item") ? new ItemCheckModel(model) : model;
		if (!id.getVariant().isEmpty()) return model;
		return switch (id.getPath()) {
			case "terrain_block" -> {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi model plugin OK ({} after it was baked)", id);
				yield new CheckModel(model, "frapi terrain OK");
			}
			case "nested_block" -> new MultiPartBakedModel(List.<Pair<Predicate<BlockState>, BakedModel>>of(Pair.of(state -> true,
					new CheckModel(model, "frapi nested terrain OK"))));
			case "broken_block" -> new BrokenModel(model);
			case "broken_nested_block" -> new MultiPartBakedModel(List.<Pair<Predicate<BlockState>, BakedModel>>of(Pair.of(state -> true, model),
					Pair.of(state -> true, new BrokenPartModel(model))));
			case "wrapped_broken_block" -> new WrapperCheckModel(new BrokenWrappedModel(model));
			default -> model;
		};
	}

	/**
	 * Which seam sent the block being drawn to Fabric's renderer: {@code "Fabricated"}, or {@code "other"} when another
	 * mod's renderer hooks did (a renderer mod, or upstream's own hooks). Walks the stack: logged once per check.
	 */
	static String seam() {
		return StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getClassName().startsWith(FABRICATED_SEAM))) ? "Fabricated" : "other";
	}

	/**
	 * What the game is drawing, by the innermost of its frames on the stack that says: {@code "breaking"} for the breaking
	 * overlay (Forge's overload of the block renderer's method for it, which keeps its name at runtime), {@code "piston"}
	 * for a block a piston moves (Forge's hook for it, or the piston head renderer), {@code "falling"} for a falling block,
	 * or null. This class's own frames don't count.
	 */
	private static @Nullable String drawing() {
		String self = FrapiClientTestMod.class.getName();
		return StackWalker.getInstance().walk(frames -> frames.filter(frame -> !frame.getClassName().startsWith(self))
				.map(frame -> frame.getMethodName().contains("Breaking") ? "breaking"
						: frame.getMethodName().equals("renderPistonMovedBlocks") || frame.getClassName().endsWith(".PistonHeadRenderer") ? "piston"
						: frame.getClassName().endsWith(".FallingBlockRenderer") ? "falling" : null)
				.filter(Objects::nonNull).findFirst().orElse(null));
	}

	/** A model as Fabric mods write one for Fabric's renderer API on 1.20.1, around the vanilla model whose quads it emits. */
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

	/** Its geometry is only the quads it emits: none for Forge's renderer. Logs {@code check} the first time a chunk section draws it. */
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

		/** Whether it has been drawn as a falling block, as a block a piston moves and with the breaking overlay. */
		static boolean movingDrawn() {
			return FALLING.get() && PISTON.get() && BREAKING.get();
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
					FrapiTestMod.LOGGER.info("FABRICATED-TEST {} ({} in {} on {}, seam={})", check, state, level.getClass().getName(), Thread.currentThread().getName(), seam());
				}
				return;
			}
			if (FALLING.get() && PISTON.get() && BREAKING.get()) return;
			String drawing = drawing();
			if ("breaking".equals(drawing) && !BREAKING.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi breaking OK ({} in {}, seam={})", state, level.getClass().getName(), seam());
			} else if ("piston".equals(drawing) && !PISTON.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi piston OK ({} in {}, seam={})", state, level.getClass().getName(), seam());
			} else if ("falling".equals(drawing) && !FALLING.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi moving block OK ({} in {}, seam={})", state, level.getClass().getName(), seam());
			}
		}
	}

	/**
	 * The test item's model: its geometry is only the quads it emits (the vanilla item model's), none for Forge's
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

		/** Whether it has been drawn in each place it is put. */
		static boolean allDrawn() {
			return LOGGED.values().stream().allMatch(AtomicBoolean::get);
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
	 * A Fabric model that throws the first time a chunk section draws it through Fabricated's seam, after emitting the
	 * wrapped model's quads and pushing a transform it never pops. Its vanilla quads are the wrapped model's: it logs once
	 * when Forge's renderer then asks for them.
	 */
	private abstract static class ThrowingModel extends FabricModel {
		private final String name;
		private final AtomicBoolean threw = new AtomicBoolean();
		private final AtomicBoolean fellBack = new AtomicBoolean();

		ThrowingModel(BakedModel wrapped, String name) {
			super(wrapped);
			this.name = name;
		}

		/** Whether it threw (it throws once). */
		boolean threw() {
			return threw.get();
		}

		@Override
		public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
			if (threw.get() && !fellBack.getAndSet(true)) FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi {} fallback OK", name);
			return wrapped.getQuads(state, direction, random);
		}

		@Override
		public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier, RenderContext context) {
			emitWrapped(level, state, pos, randomSupplier, context);
			// Only through Fabricated's seam: another mod's renderer hooks aren't Fabricated's to guard.
			if (!(level instanceof RenderChunkRegion) || threw.get() || !seam().equals("Fabricated") || threw.getAndSet(true)) return;
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

	/** The model a {@link WrapperCheckModel} wraps on the wrapped broken test block: its throw is its own, not the wrapper's. */
	private static final class BrokenWrappedModel extends ThrowingModel {
		BrokenWrappedModel(BakedModel wrapped) {
			super(wrapped, "wrapped model");
		}
	}

	/**
	 * A Fabric model wrapping another Fabric model, as Continuity's connected texture models wrap the models they are
	 * given: it emits what the model it wraps emits, by calling that model's emitter, and gives Forge's renderer that
	 * model's quads. On the wrapped broken test block it wraps one that throws (FRAPI rule 8): the throw is put down to
	 * the wrapped model, so this class keeps Fabric's renderer. Logs once when Fabricated calls its emitter for a chunk
	 * section after the model it wraps threw.
	 */
	private static final class WrapperCheckModel extends FabricModel {
		private static final AtomicBoolean KEPT = new AtomicBoolean();
		private final ThrowingModel throwing;

		WrapperCheckModel(ThrowingModel wrapped) {
			super(wrapped);
			this.throwing = wrapped;
		}

		@Override
		public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
			return wrapped.getQuads(state, direction, random);
		}

		@Override
		public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier, RenderContext context) {
			boolean afterThrow = throwing.threw();
			emitWrapped(level, state, pos, randomSupplier, context);
			if (afterThrow && level instanceof RenderChunkRegion && !KEPT.get() && seam().equals("Fabricated") && !KEPT.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi wrapper kept OK (Fabricated still calls its emitter, at {}, after the model it wraps threw)", pos);
			}
		}
	}
}
