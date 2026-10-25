package io.github.cmdralph.fabricated.neoforge.rendering;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.render.AltModelBlockRenderer;
import net.fabricmc.fabric.api.client.renderer.v1.render.ChunkSectionLayerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the block models only Fabric's renderer API can draw where NeoForge draws blocks: chunk sections, moving blocks
 * (pushed by pistons, falling) and the block breaking overlay.
 *
 * <p>A model a Fabric mod implements itself (its own {@code emitQuads}) has no geometry NeoForge's block renderer can
 * see, so it was invisible. Upstream sends every block through Fabric's renderer; on NeoForge that would lose what
 * NeoForge adds to block rendering (level-aware model parts, dynamic tints, face culling and ambient occlusion per
 * part). So only a block whose model is a Fabric model, or a vanilla multipart or weighted model with a Fabric model
 * among its parts, goes through Fabric's renderer (Indigo, or whichever renderer is registered); every other block
 * keeps NeoForge's path.
 *
 * <p>Sections compile on several worker threads at once: the state here is immutable, or confined to one thread.
 */
public final class FabricBlockRendering {
	/** Fabric's renderer API is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-renderer-api-v1");

	private FabricBlockRendering() {
	}

	/** The section compiler's per-layer buffers, which Fabricated's mixin exposes. */
	public interface SectionLayers {
		BufferBuilder fabricated$layer(Map<ChunkSectionLayer, BufferBuilder> startedLayers, SectionBufferBuilderPack builders, ChunkSectionLayer layer);
	}

	/** A moving block renderer's buffers, which Fabricated's mixin exposes. */
	public interface MovingBlockBuffers {
		VertexConsumer fabricated$buffer(RenderType type);

		/** The buffer of the render type's outline, for an outlined block (a glowing falling block); null if none. */
		@Nullable VertexConsumer fabricated$outlineBuffer(RenderType type);
	}

	/**
	 * A vanilla model made of other models (multipart, weighted variants), which Fabricated's mixins let say whether one
	 * of them is drawn by Fabric's renderer. The answer is kept in the model, which a resource reload replaces.
	 */
	public interface Composite {
		boolean fabricated$hasFabricPart();
	}

	/** Whether this block model is drawn by Fabric's renderer instead of NeoForge's. */
	public static boolean draws(BlockStateModel model) {
		return ENABLED && Frapi.isFabricModel(model);
	}

	/** Whether any of a composite model's parts is drawn by Fabric's renderer. */
	public static boolean anyDrawn(List<BlockStateModel> models) {
		for (BlockStateModel model : models) {
			if (draws(model)) return true;
		}
		return false;
	}

	/** Draws one block of a chunk section, as NeoForge's block renderer would have with the same arguments. */
	public static void tesselate(SectionLayers layers, Map<ChunkSectionLayer, BufferBuilder> startedLayers, SectionBufferBuilderPack builders,
			boolean ambientOcclusion, BlockColors blockColors,
			float x, float y, float z, BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed) {
		Frapi.tesselate(layers, startedLayers, builders, ambientOcclusion, blockColors, x, y, z, level, pos, state, model, seed);
	}

	/**
	 * Draws one moving block, as NeoForge's block renderer would have with the same arguments, into the moving block
	 * render type of each quad's layer ({@code forceTranslucent}: the translucent one for all).
	 */
	public static void tesselateMoving(MovingBlockBuffers buffers, PoseStack.Pose pose, boolean forceTranslucent, int outlineColor,
			boolean ambientOcclusion, BlockColors blockColors,
			float x, float y, float z, BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed) {
		Frapi.tesselateMoving(buffers, pose, forceTranslucent, outlineColor, ambientOcclusion, blockColors, x, y, z, level, pos, state, model, seed);
	}

	/**
	 * Adds a Fabric model's geometry to the parts the block breaking overlay draws, as one part: the overlay only takes
	 * parts, and only their shape counts there.
	 */
	public static void collectBreakingParts(BlockStateModel model, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			List<BlockStateModelPart> parts) {
		Frapi.collectBreakingParts(model, level, pos, state, random, parts);
	}

	/**
	 * Registers Fabric API's own renderer (Indigo) when none registered while the client initializers ran (called once
	 * they ran), so Fabric mods' meshes and emitters, and Fabric block models, work. Indigo stands down for any mod saying
	 * it contains a Fabric renderer ({@code fabric-renderer-api-v1:contains_renderer}), and a NeoForge mod can say so of
	 * a build without one (Sodium for NeoForge on 1.21.11, read in its source), which left Fabric's renderer API with
	 * none. Indigo's own hooks stay off; Fabricated's don't need them. Does nothing without the renderer API.
	 */
	public static void registerFallbackRenderer() {
		if (ENABLED) RendererFallback.register();
	}

	private static ReportedException crash(Throwable t, String what, BlockState state, BlockPos pos, BlockStateModel model) {
		CrashReport report = CrashReport.forThrowable(t, what);
		CrashReportCategory category = report.addCategory("Block model being drawn");
		category.setDetail("Block", state);
		category.setDetail("Position", pos);
		category.setDetail("Model class", model.getClass().getName());
		return new ReportedException(report);
	}

	/** Names Fabric's renderer API, so loaded only when called; apart from {@link Frapi}, which keeps the renderer it first finds. */
	private static final class RendererFallback {
		private static final String INDIGO = "net.fabricmc.fabric.impl.client.indigo.renderer.IndigoRenderer";

		static void register() {
			try {
				Renderer.get();
				return;
			} catch (UnsupportedOperationException e) {
				// None registered: Indigo's, if Fabric API's renderer module is installed.
			}
			Logger logger = LoggerFactory.getLogger("Fabricated");
			Renderer indigo;
			try {
				indigo = (Renderer) Class.forName(INDIGO, true, FabricBlockRendering.class.getClassLoader()).getField("INSTANCE").get(null);
			} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
				logger.warn("Fabric's renderer API has no renderer, and Fabric API's own renderer isn't installed: Fabric block models stay invisible ({})", e.toString());
				return;
			}
			Renderer.register(indigo);
			logger.warn("No Fabric renderer registered although a mod says it contains one: Fabric API's own renderer (Indigo) is used, without its own hooks");
		}
	}

	/** Everything that names Fabric's renderer API: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Frapi {
		private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
		/** The registered renderer. Null if Fabric's renderer API has none (Indigo disabled and no other renderer). */
		private static final @Nullable Renderer RENDERER = findRenderer();
		/**
		 * Whether a model class implements {@code emitQuads} itself. Vanilla's model classes get upstream's equivalent
		 * of NeoForge's path (its own mixins), and models without an implementation use the interface's default, which
		 * only draws the parts NeoForge's renderer also sees: neither needs Fabric's renderer.
		 */
		private static final ClassValue<Boolean> FABRIC_MODEL = new ClassValue<>() {
			@Override
			protected Boolean computeValue(Class<?> type) {
				Class<?> owner;
				try {
					owner = type.getMethod("emitQuads", QuadEmitter.class, BlockAndTintGetter.class, BlockPos.class, BlockState.class,
							RandomSource.class, Predicate.class).getDeclaringClass();
				} catch (NoSuchMethodException e) {
					return false; // not a FabricBlockStateModel: Fabric's renderer API didn't apply to this class
				} catch (LinkageError e) {
					// getMethod resolves the types every public method names: one from a mod that isn't installed throws. Asked
					// while a chunk section compiles, so it must not escape; the class keeps NeoForge's renderer.
					LOGGER.warn("Block models of class {} are left to NeoForge's renderer: a type their methods name is missing ({})", type.getName(), e.toString());
					return false;
				}
				boolean fabricModel = owner != FabricBlockStateModel.class && !owner.getName().startsWith("net.minecraft.");
				if (fabricModel) LOGGER.debug("Block models of class {} are drawn with Fabric's renderer", type.getName());
				return fabricModel;
			}
		};
		/** The breaking overlay draws the whole model: no face is culled. */
		private static final Predicate<@Nullable Direction> NO_CULLING = direction -> false;
		/** Each thread's renderers and emitters, reused for every block it draws. */
		private static final ThreadLocal<SectionState> SECTION = new ThreadLocal<>();
		private static final ThreadLocal<MovingState> MOVING = new ThreadLocal<>();
		private static final ThreadLocal<BreakingState> BREAKING = new ThreadLocal<>();

		/**
		 * Resolved once, when the first block is drawn: renderers register during client initialization (Indigo in its
		 * client entrypoint), before any world renders.
		 */
		private static @Nullable Renderer findRenderer() {
			try {
				Renderer renderer = Renderer.get();
				LOGGER.info("Fabric block models are drawn by the renderer {}", renderer.getClass().getName());
				return renderer;
			} catch (UnsupportedOperationException e) {
				LOGGER.warn("Fabric's renderer API has no renderer registered: Fabric block models stay invisible in the world", e);
				return null;
			}
		}

		static boolean isFabricModel(BlockStateModel model) {
			return RENDERER != null && (FABRIC_MODEL.get(model.getClass()) || model instanceof Composite composite && composite.fabricated$hasFabricPart());
		}

		static void tesselate(SectionLayers layers, Map<ChunkSectionLayer, BufferBuilder> startedLayers, SectionBufferBuilderPack builders,
				boolean ambientOcclusion, BlockColors blockColors,
				float x, float y, float z, BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed) {
			SectionState section = SECTION.get();
			if (section == null || section.ambientOcclusion != ambientOcclusion || section.blockColors != blockColors) {
				// Once per thread, and again when the smooth lighting option changes (the game makes a new compiler).
				section = new SectionState(RENDERER, ambientOcclusion, blockColors);
				SECTION.set(section);
			}
			section.layers = layers;
			section.startedLayers = startedLayers;
			section.builders = builders;
			try {
				section.renderer.tesselateBlock(section.emitter, x, y, z, level, pos, state, model, seed);
			} catch (VirtualMachineError | ReportedException e) {
				throw e;
			} catch (Throwable t) {
				throw crash(t, "Tesselating block model with Fabric's renderer", state, pos, model);
			} finally {
				// The section's buffers are only reachable while it compiles.
				section.layers = null;
				section.startedLayers = null;
				section.builders = null;
			}
		}

		static void tesselateMoving(MovingBlockBuffers buffers, PoseStack.Pose pose, boolean forceTranslucent, int outlineColor,
				boolean ambientOcclusion, BlockColors blockColors,
				float x, float y, float z, BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed) {
			MovingState moving = MOVING.get();
			if (moving == null || moving.ambientOcclusion != ambientOcclusion || moving.blockColors != blockColors) {
				moving = new MovingState(RENDERER, ambientOcclusion, blockColors);
				MOVING.set(moving);
			}
			moving.buffers = buffers;
			moving.pose = pose;
			moving.forceTranslucent = forceTranslucent;
			moving.outlineColor = outlineColor;
			try {
				moving.renderer.tesselateBlock(moving.emitter, x, y, z, level, pos, state, model, seed);
			} catch (VirtualMachineError | ReportedException e) {
				throw e;
			} catch (Throwable t) {
				throw crash(t, "Tesselating moving block model with Fabric's renderer", state, pos, model);
			} finally {
				moving.buffers = null;
				moving.pose = null;
			}
		}

		static void collectBreakingParts(BlockStateModel model, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
				List<BlockStateModelPart> parts) {
			BreakingState breaking = BREAKING.get();
			if (breaking == null) {
				breaking = new BreakingState(RENDERER);
				BREAKING.set(breaking);
			}
			Material.Baked particle = model.particleMaterial(level, pos, state);
			breaking.sprite = particle.sprite();
			try {
				// Fabric's renderer API adds FabricBlockStateModel to every block model when the game loads.
				((FabricBlockStateModel) model).emitQuads(breaking.emitter, level, pos, state, random, NO_CULLING);
				// The overlay is drawn after this returns (the parts are submitted), so the part keeps its own copy.
				parts.add(new EmittedPart(List.copyOf(breaking.quads), particle));
			} catch (VirtualMachineError | ReportedException e) {
				throw e;
			} catch (Throwable t) {
				throw crash(t, "Emitting block model quads for the breaking overlay", state, pos, model);
			} finally {
				breaking.quads.clear();
				breaking.sprite = null;
			}
		}
	}

	/**
	 * One thread's Fabric block renderer for chunk sections and the emitter it outputs to. The renderer (Indigo's keeps
	 * the thread's light cache) and its emitter are confined to the thread that made them; the section being compiled
	 * is set per block.
	 */
	private static final class SectionState implements Consumer<MutableQuadView> {
		final boolean ambientOcclusion;
		final BlockColors blockColors;
		final AltModelBlockRenderer renderer;
		final QuadEmitter emitter;
		@Nullable SectionLayers layers;
		@Nullable Map<ChunkSectionLayer, BufferBuilder> startedLayers;
		@Nullable SectionBufferBuilderPack builders;

		SectionState(Renderer renderer, boolean ambientOcclusion, BlockColors blockColors) {
			this.ambientOcclusion = ambientOcclusion;
			this.blockColors = blockColors;
			// cull = true, as for vanilla's section compiler.
			this.renderer = renderer.altModelBlockRenderer(ambientOcclusion, true, blockColors);
			this.emitter = renderer.quadEmitter(this);
		}

		/**
		 * A finished quad (lit, tinted and offset by the renderer) goes to its layer's buffer, as upstream does. Quads
		 * only arrive during {@code tesselateBlock}, while the section's buffers are set.
		 */
		@Override
		public void accept(MutableQuadView quad) {
			quad.buffer(OverlayTexture.NO_OVERLAY, layers.fabricated$layer(startedLayers, builders, quad.chunkLayer()));
		}
	}

	/** One thread's Fabric block renderer for moving blocks, as {@link SectionState} is for sections. */
	private static final class MovingState implements Consumer<MutableQuadView> {
		final boolean ambientOcclusion;
		final BlockColors blockColors;
		final AltModelBlockRenderer renderer;
		final QuadEmitter emitter;
		@Nullable MovingBlockBuffers buffers;
		PoseStack.@Nullable Pose pose;
		boolean forceTranslucent;
		int outlineColor;

		MovingState(Renderer renderer, boolean ambientOcclusion, BlockColors blockColors) {
			this.ambientOcclusion = ambientOcclusion;
			this.blockColors = blockColors;
			// cull = false, as for vanilla's moving blocks: a moving block's neighbours don't hide its faces.
			this.renderer = renderer.altModelBlockRenderer(ambientOcclusion, false, blockColors);
			this.emitter = renderer.quadEmitter(this);
		}

		/**
		 * A finished quad goes to the moving block render type of its layer, as upstream does: an outlined block's to
		 * that type's outline, in the outline's colour.
		 */
		@Override
		public void accept(MutableQuadView quad) {
			ChunkSectionLayer layer = forceTranslucent ? ChunkSectionLayer.TRANSLUCENT : quad.chunkLayer();
			RenderType type = ChunkSectionLayerHelper.getMovingBlockRenderType(layer);
			VertexConsumer outline = outlineColor != 0 ? buffers.fabricated$outlineBuffer(type) : null;
			if (outline != null) {
				for (int i = 0; i < 4; i++) quad.color(i, outlineColor);
				quad.buffer(OverlayTexture.NO_OVERLAY, pose, outline);
			} else {
				quad.buffer(OverlayTexture.NO_OVERLAY, pose, buffers.fabricated$buffer(type));
			}
		}
	}

	/** One thread's emitter for the breaking overlay, which turns a model's quads back into vanilla quads. */
	private static final class BreakingState implements Consumer<MutableQuadView> {
		final QuadEmitter emitter;
		final List<BakedQuad> quads = new ArrayList<>();
		@Nullable TextureAtlasSprite sprite;

		BreakingState(Renderer renderer) {
			this.emitter = renderer.quadEmitter(this);
		}

		@Override
		public void accept(MutableQuadView quad) {
			// The overlay draws its own texture over the quad's shape: the sprite only completes the vanilla quad.
			quads.add(quad.toBakedQuad(sprite));
		}
	}

	/** A Fabric model's quads as one model part (none tied to a face, so none is culled). */
	private record EmittedPart(List<BakedQuad> quads, Material.Baked particleMaterial) implements BlockStateModelPart {
		@Override
		public List<BakedQuad> getQuads(@Nullable Direction direction) {
			return direction == null ? quads : List.of();
		}

		@Override
		public boolean useAmbientOcclusion() {
			return true;
		}

		@Override
		public int materialFlags() {
			return 0;
		}
	}
}
