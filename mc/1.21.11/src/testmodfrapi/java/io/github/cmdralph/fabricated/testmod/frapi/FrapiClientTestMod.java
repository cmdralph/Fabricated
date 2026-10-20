package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadTransform;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.EmptyBlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;

/**
 * Fabric models (their own {@code emitQuads}, and no parts), which NeoForge's block renderer can't draw: custom
 * blockstate model types ({@code fabric:type}), one the test block's model, the other the part of the nested test block's
 * vanilla multipart model. Each emits the quads of a NeoForge model with no {@code emitQuads} of its own, drawn through
 * Fabricated's NeoForge-aware default, and logs once when a chunk section draws it (vanilla's, or Sodium's), once when it
 * is drawn as a moving block and once with the breaking overlay. Once a world is loaded, the parts NeoForge's renderers
 * get from the test block's model are checked to be made from its quads, with NeoForge's baked colour kept (1.21.11).
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

	/** Whether NeoForge's breaking overlay is being drawn: other callers (the block outline pass) pass the level too. */
	private static boolean drawingBreakingOverlay() {
		return StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getMethodName().equals("renderBreakingTexture")));
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
			if (isSection(level) && !logged.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST {} ({} in {} on {})", check, state, level.getClass().getName(), Thread.currentThread().getName());
			} else if (level instanceof MovingBlockRenderState && !MOVING.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi moving block OK ({})", state);
			} else if (level instanceof Level && !BREAKING.get() && drawingBreakingOverlay() && !BREAKING.getAndSet(true)) {
				FrapiTestMod.LOGGER.info("FABRICATED-TEST frapi breaking OK ({})", state);
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
}
