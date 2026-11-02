package io.github.cmdralph.fabricated.neoforge.rendering;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.joml.Matrix4fc;
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
import net.fabricmc.fabric.api.client.renderer.v1.render.FabricBlockModelRenderState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelRenderState;
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
 * (pushed by pistons, falling), blocks drawn as entities (a minecart's contents, an enderman's block) and the block
 * breaking overlay.
 *
 * <p>A model a Fabric mod implements itself (its own {@code emitQuads}) has no geometry NeoForge's block renderer can
 * see, so it was invisible. Upstream sends every block through Fabric's renderer; on NeoForge that would lose what
 * NeoForge adds to block rendering (level-aware model parts, dynamic tints, face culling and ambient occlusion per
 * part). So only a block whose model is a Fabric model, or a vanilla multipart or weighted model with a Fabric model
 * among its parts, goes through Fabric's renderer (Indigo, or whichever renderer is registered); every other block
 * keeps NeoForge's path.
 *
 * <p>A Fabric model that throws there fails alone (FRAPI rule 8, {@link FrapiFailures}): the block is drawn by NeoForge
 * instead, as is every model of the class that threw from then on (not of a class wrapping it:
 * {@link FrapiFailures#putDown}), and the thread's Fabric renderer and emitter are replaced (a
 * model that threw may have left a transform pushed on the emitter).
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
	 * of them is drawn by Fabric's renderer, or broken. The answer is kept in the model, which a resource reload replaces,
	 * for one {@link FrapiFailures#generation()}.
	 */
	public interface Composite {
		/** {@link FrapiFailures#NONE}, {@link FrapiFailures#FABRIC} or {@link FrapiFailures#BROKEN}, from {@link #state(List)}. */
		int fabricated$state();

		/** The models it is made of (any its state selects). Asked on the failure path only. */
		List<BlockStateModel> fabricated$parts();
	}

	/** Whether this block model is drawn by Fabric's renderer instead of NeoForge's. */
	public static boolean draws(BlockStateModel model) {
		return state(model) == FrapiFailures.FABRIC;
	}

	/** Whether Fabric's renderer draws this model, or would if drawing it hadn't thrown ({@link FrapiFailures}). */
	public static int state(BlockStateModel model) {
		return ENABLED ? Frapi.state(model) : FrapiFailures.NONE;
	}

	/** A composite model's state, from its parts' (a composite keeps it: see {@link Composite}). */
	public static int state(List<BlockStateModel> parts) {
		int state = FrapiFailures.NONE;
		for (BlockStateModel part : parts) {
			state = FrapiFailures.combine(state, state(part));
			if (state == FrapiFailures.BROKEN) break;
		}
		return state;
	}

	/**
	 * Draws one block of a chunk section, as NeoForge's block renderer would have with the same arguments. False if the
	 * model threw: NeoForge's renderer then draws the block (quads already sent stay in this build of the section).
	 */
	public static boolean tesselate(SectionLayers layers, Map<ChunkSectionLayer, BufferBuilder> startedLayers, SectionBufferBuilderPack builders,
			boolean ambientOcclusion, BlockColors blockColors,
			float x, float y, float z, BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed) {
		return Frapi.tesselate(layers, startedLayers, builders, ambientOcclusion, blockColors, x, y, z, level, pos, state, model, seed);
	}

	/**
	 * Draws one moving block, as NeoForge's block renderer would have with the same arguments, into the moving block
	 * render type of each quad's layer ({@code forceTranslucent}: the translucent one for all). False if the model threw:
	 * NeoForge's renderer then draws the block.
	 */
	public static boolean tesselateMoving(MovingBlockBuffers buffers, PoseStack.Pose pose, boolean forceTranslucent, int outlineColor,
			boolean ambientOcclusion, BlockColors blockColors,
			float x, float y, float z, BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed) {
		return Frapi.tesselateMoving(buffers, pose, forceTranslucent, outlineColor, ambientOcclusion, blockColors, x, y, z, level, pos, state, model, seed);
	}

	/**
	 * Fills the render state of a block drawn as an entity (a minecart's contents, an enderman's block, a block display)
	 * with a Fabric model's quads, as upstream does for every model: into the render state's mesh (Fabric's renderer
	 * API's), which Fabric's renderer draws once the state is submitted. False if the model threw: NeoForge then fills
	 * the state from the model's parts, which drops the mesh and the quads already in it.
	 */
	public static boolean updateEntityBlock(BlockModelRenderState output, Matrix4fc transformation, BlockStateModel model, BlockState state, long seed) {
		return Frapi.updateEntityBlock(output, transformation, model, state, seed);
	}

	/**
	 * Adds a Fabric model's geometry to the parts the block breaking overlay draws, as one part: the overlay only takes
	 * parts, and only their shape counts there. False, adding nothing, if the model threw: NeoForge then collects its
	 * parts.
	 */
	public static boolean collectBreakingParts(BlockStateModel model, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			List<BlockStateModelPart> parts) {
		return Frapi.collectBreakingParts(model, level, pos, state, random, parts);
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
		/** Fabric's block model method, the frame a model's throw shows. */
		private static final String EMIT = "emitQuads";
		/**
		 * Whether a model class implements {@code emitQuads} itself, and whether that threw. Vanilla's model classes get
		 * upstream's equivalent of NeoForge's path (its own mixins), and models without an implementation use the
		 * interface's default, which only draws the parts NeoForge's renderer also sees: neither needs Fabric's renderer.
		 */
		private static final ClassValue<FrapiFailures.ModelClass> MODEL_CLASSES = new ClassValue<>() {
			@Override
			protected FrapiFailures.ModelClass computeValue(Class<?> type) {
				Class<?> owner;
				try {
					owner = type.getMethod(EMIT, QuadEmitter.class, BlockAndTintGetter.class, BlockPos.class, BlockState.class,
							RandomSource.class, Predicate.class).getDeclaringClass();
				} catch (NoSuchMethodException e) {
					return FrapiFailures.ModelClass.NOT_FABRIC; // not a FabricBlockStateModel: Fabric's renderer API didn't apply to this class
				} catch (LinkageError e) {
					// getMethod resolves the types every public method names: one from a mod that isn't installed throws. Asked
					// while a chunk section compiles, so it must not escape; the class keeps NeoForge's renderer.
					LOGGER.warn("Block models of class {} are left to NeoForge's renderer: a type their methods name is missing ({})", type.getName(), e.toString());
					return FrapiFailures.ModelClass.NOT_FABRIC;
				}
				if (owner == FabricBlockStateModel.class || owner.getName().startsWith("net.minecraft.")) return FrapiFailures.ModelClass.NOT_FABRIC;
				LOGGER.debug("Block models of class {} are drawn with Fabric's renderer", type.getName());
				return new FrapiFailures.ModelClass(true, owner.getName());
			}
		};
		/** The breaking overlay draws the whole model: no face is culled. */
		private static final Predicate<@Nullable Direction> NO_CULLING = direction -> false;
		/** Each thread's renderers and emitters, reused for every block it draws. */
		private static final ThreadLocal<SectionState> SECTION = new ThreadLocal<>();
		private static final ThreadLocal<MovingState> MOVING = new ThreadLocal<>();
		private static final ThreadLocal<BreakingState> BREAKING = new ThreadLocal<>();
		private static final ThreadLocal<EntityState> ENTITY = new ThreadLocal<>();

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

		static int state(BlockStateModel model) {
			if (RENDERER == null) return FrapiFailures.NONE;
			FrapiFailures.ModelClass type = MODEL_CLASSES.get(model.getClass());
			if (type.fabric()) return type.broken() ? FrapiFailures.BROKEN : FrapiFailures.FABRIC;
			return model instanceof Composite composite ? composite.fabricated$state() : FrapiFailures.NONE;
		}

		/**
		 * Marks what threw broken: the model's class if it is a Fabric model, or its Fabric parts in a composite, as the
		 * stack trace puts the throw down to them ({@link FrapiFailures#putDown}: not a wrapper, when a model it wraps threw).
		 */
		static void markBroken(BlockStateModel model, Throwable error, String where, BlockState state) {
			List<BlockStateModel> fabricModels = new ArrayList<>();
			fabricModels(model, fabricModels, 0);
			FrapiFailures.putDown(model, fabricModels, MODEL_CLASSES, EMIT, error, where, state);
		}

		/** The Fabric models a model is, or is made of (through nested composites, to a depth no vanilla model reaches). */
		private static void fabricModels(BlockStateModel model, List<BlockStateModel> out, int depth) {
			if (MODEL_CLASSES.get(model.getClass()).fabric()) {
				out.add(model);
			} else if (model instanceof Composite composite && depth < 8) {
				List<BlockStateModel> parts;
				try {
					parts = composite.fabricated$parts();
				} catch (RuntimeException | LinkageError e) {
					return; // a part selector that throws: the parts found elsewhere are marked
				}
				for (BlockStateModel part : parts) fabricModels(part, out, depth + 1);
			}
		}

		static boolean tesselate(SectionLayers layers, Map<ChunkSectionLayer, BufferBuilder> startedLayers, SectionBufferBuilderPack builders,
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
				return true;
			} catch (VirtualMachineError e) {
				throw e;
			} catch (Throwable t) {
				SECTION.remove();
				markBroken(model, t, "a chunk section at " + pos, state);
				return false;
			} finally {
				// The section's buffers are only reachable while it compiles.
				section.layers = null;
				section.startedLayers = null;
				section.builders = null;
			}
		}

		static boolean tesselateMoving(MovingBlockBuffers buffers, PoseStack.Pose pose, boolean forceTranslucent, int outlineColor,
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
				return true;
			} catch (VirtualMachineError e) {
				throw e;
			} catch (Throwable t) {
				MOVING.remove();
				markBroken(model, t, "a moving block at " + pos, state);
				return false;
			} finally {
				moving.buffers = null;
				moving.pose = null;
			}
		}

		static boolean updateEntityBlock(BlockModelRenderState output, Matrix4fc transformation, BlockStateModel model, BlockState state, long seed) {
			EntityState entity = ENTITY.get();
			if (entity == null) {
				entity = new EntityState(RENDERER);
				ENTITY.set(entity);
			}
			try {
				// As upstream's hook: no level (an entity's block isn't in one), and Fabric's own translucency check.
				FabricBlockStateModel fabricModel = (FabricBlockStateModel) model;
				boolean translucent = fabricModel.hasMaterialFlag(BlockAndTintGetter.EMPTY, BlockPos.ZERO, state, output.scratchRandomSource(seed), BakedQuad.FLAG_TRANSLUCENT);
				entity.mesh = ((FabricBlockModelRenderState) output).setupMesh(transformation, translucent);
				fabricModel.emitQuads(entity.emitter, BlockAndTintGetter.EMPTY, BlockPos.ZERO, state, output.scratchRandomSource(seed), NO_CULLING);
				return true;
			} catch (VirtualMachineError e) {
				throw e;
			} catch (Throwable t) {
				// The thread's emitter may keep a transform the model pushed. The quads already in the render state's mesh are
				// dropped when NeoForge fills the state from the model's parts.
				ENTITY.remove();
				markBroken(model, t, "an entity (a block it carries or shows)", state);
				return false;
			} finally {
				entity.mesh = null;
			}
		}

		static boolean collectBreakingParts(BlockStateModel model, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
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
				return true;
			} catch (VirtualMachineError e) {
				throw e;
			} catch (Throwable t) {
				BREAKING.remove();
				markBroken(model, t, "the breaking overlay at " + pos, state);
				return false;
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

	/**
	 * One thread's emitter for blocks drawn as entities, which copies each finished quad into the mesh of the render state
	 * being filled. The model emits into this emitter, never into the mesh's own: Fabric's renderer keeps a mesh's emitter
	 * (and the transforms pushed on it) across clears, so a model that throws with a transform pushed would leave it for
	 * the next block drawn into that render state.
	 */
	private static final class EntityState implements Consumer<MutableQuadView> {
		final QuadEmitter emitter;
		@Nullable QuadEmitter mesh;

		EntityState(Renderer renderer) {
			this.emitter = renderer.quadEmitter(this);
		}

		/** Quads only arrive during {@code emitQuads}, while the mesh is set. */
		@Override
		public void accept(MutableQuadView quad) {
			mesh.copyFrom(quad);
			mesh.emit();
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
