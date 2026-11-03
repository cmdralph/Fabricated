package io.github.cmdralph.fabricated.neoforge.rendering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.cmdralph.fabricated.core.runtime.HeldThrows;
import io.github.cmdralph.fabricated.neoforge.rendering.mixin.MutableQuadViewAccessor;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.fabricmc.fabric.api.renderer.v1.sprite.FabricSpriteAtlasTexture;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.SimpleModelWrapper;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import net.neoforged.neoforge.client.model.quad.BakedNormals;

/**
 * Lets NeoForge's renderers, and the renderer mods that replace them, draw the block models only Fabric's renderer API
 * can draw (1.21.11).
 *
 * <p>A model a Fabric mod implements itself (its own {@code emitQuads}) has no geometry NeoForge's block renderer can
 * see, so it was invisible. Upstream sends every block through Fabric's renderer; on NeoForge that would lose what
 * NeoForge adds to block rendering (level-aware model parts, per-part render layers). So:
 * <ul>
 * <li>in chunk sections NeoForge compiles, only a block whose model is a Fabric model, or a vanilla multipart or
 * weighted model with a Fabric model among its parts, goes through Fabric's renderer (Indigo, or whichever renderer is
 * registered), into NeoForge's buffer for each quad's layer; every other block keeps NeoForge's path;</li>
 * <li>everywhere else NeoForge asks a model for its parts ({@link #collectParts}): moving blocks, the breaking overlay,
 * blocks drawn as entities, and the chunk sections of a mod that meshes them itself (Sodium for NeoForge has no Fabric
 * renderer on 1.21.11), a Fabric model's parts are made from the quads it emits.</li>
 * </ul>
 *
 * <p>A Fabric model that throws there fails alone (FRAPI rule 8, {@link FrapiFailures}): the block is drawn by NeoForge
 * instead, from the parts the model's vanilla method gives, as is every model of the class that threw from then on (not
 * of a class wrapping it: {@link FrapiFailures#putDown}). A throw inside a model another wraps through Fabric's
 * {@code WrapperBlockStateModel} is held there until the draw here returns ({@link HeldThrows}), so the wrapper finishes
 * its call: Continuity's keeps a per-thread transform active through it, and a throw unwinding it left that thread
 * without connected textures for the rest of the session (CORPUS-56).
 *
 * <p>Sections compile on several worker threads at once: the state here is immutable or per thread, and the renderer
 * keeps its own per-thread state.
 */
public final class FabricBlockRendering {
	/** Fabric's renderer API is installed. Its classes are only touched then; without it no block is looked at. */
	public static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-renderer-api-v1");

	private FabricBlockRendering() {
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
	public static boolean tesselate(BlockRenderDispatcher dispatcher, BlockAndTintGetter level, BlockStateModel model, BlockState state, BlockPos pos,
			PoseStack poseStack, Function<ChunkSectionLayer, VertexConsumer> buffers, boolean cull) {
		return Frapi.tesselate(dispatcher, level, model, state, pos, poseStack, buffers, cull);
	}

	/**
	 * How many quad transforms an emitter holds: Indigo's, which a model wrapping another is given while one of the draws
	 * here holds throws ({@link HeldThrows#holding}); -1 for any other. Asked where a wrapper calls its wrapped model.
	 */
	public static int transforms(QuadEmitter emitter) {
		return emitter instanceof MutableQuadViewAccessor indigo ? indigo.fabricated$transformStack().size() : -1;
	}

	/**
	 * Pops the quad transforms a wrapped model that threw left on the emitter, down to the {@code count} it was given, so
	 * its wrapper pops its own. Failure path; the draw here drops the emitter's thread state once it returns.
	 */
	public static void dropTransforms(QuadEmitter emitter, int count) {
		if (count < 0 || !(emitter instanceof MutableQuadViewAccessor indigo)) return;
		try {
			while (indigo.fabricated$transformStack().size() > count) emitter.popTransform();
		} catch (RuntimeException e) {
			// The draw here drops the emitter: what is left on it goes with it.
		}
	}

	/**
	 * NeoForge's level-aware {@code collectParts}, for a model that doesn't implement it ({@code BlockStateModelMixin}).
	 * A Fabric model's parts are made from the quads it emits, one part per render layer, so a renderer drawing them
	 * NeoForge's way draws what Fabric's renderer would. Any other model gives the parts its vanilla method gives, as
	 * NeoForge's own default does.
	 */
	public static void collectParts(BlockStateModel model, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			List<BlockModelPart> parts) {
		// Air: a caller without the block (NeoForge's deprecated overloads), which a Fabric model can't emit for.
		if (!state.isAir() && draws(model) && Frapi.collectParts(model, level, pos, state, random, parts)) return;
		model.collectParts(random, parts);
	}

	/**
	 * Registers Fabric API's own renderer (Indigo) when none registered while the client initializers ran (called once
	 * they ran), so Fabric mods' meshes and emitters, and Fabric block models, work. Indigo stands down for any mod saying
	 * it contains a Fabric renderer ({@code fabric-renderer-api-v1:contains_renderer}), and a NeoForge mod can say so of
	 * a build without one (Sodium for NeoForge on 1.21.11, read in its source), which left Fabric's renderer API with
	 * none. Indigo's own hooks stay off; Fabricated's don't need them, and what of Indigo needs them works without them
	 * ({@link IndigoWithoutHooks}). Does nothing without the renderer API.
	 */
	public static void registerFallbackRenderer() {
		if (ENABLED) RendererFallback.register();
	}

	/** A mesh of the registered renderer, for an item layer's emitter under {@link IndigoWithoutHooks}. */
	public static MutableMesh fallbackMesh() {
		return Renderer.get().mutableMesh();
	}

	/** The mesh's quads as NeoForge's quads, added to an item layer's (see {@link FallbackLayerMesh}). */
	public static void addItemQuads(MutableMesh mesh, List<BakedQuad> quads) {
		Frapi.addItemQuads(mesh, quads);
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
			Renderer.register(new IndigoWithoutHooks(indigo));
			logger.warn("No Fabric renderer registered although a mod says it contains one: Fabric API's own renderer (Indigo) is used, without its own hooks"
					+ " (Fabric item models' emitted quads are drawn in their layer's render type)");
		}
	}

	/** Everything that names Fabric's renderer API: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Frapi {
		private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
		/** The registered renderer. Null if Fabric's renderer API has none (Indigo disabled and no other renderer). */
		private static final @Nullable Renderer RENDERER = findRenderer();
		/** Fabric's block model method, the frame a model's throw shows. */
		private static final String EMIT = "emitQuads";
		/** Indigo's per-thread context for terrain-like rendering, which a model that threw may have left a transform in. */
		private static final String INDIGO_TERRAIN_CONTEXT = "net.fabricmc.fabric.impl.client.indigo.renderer.render.TerrainLikeRenderContext";
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
		/** A model's parts hold all its quads: NeoForge's renderer culls each face itself. */
		private static final Predicate<@Nullable Direction> NO_CULLING = direction -> false;
		/** Each thread's models whose quads are being collected, and the mesh of the outermost one. */
		private static final ThreadLocal<Collecting> COLLECTING = ThreadLocal.withInitial(Collecting::new);

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

		static boolean tesselate(BlockRenderDispatcher dispatcher, BlockAndTintGetter level, BlockStateModel model, BlockState state, BlockPos pos,
				PoseStack poseStack, Function<ChunkSectionLayer, VertexConsumer> buffers, boolean cull) {
			Throwable thrown = null;
			Throwable outer = HeldThrows.begin();
			try {
				RENDERER.render(dispatcher.getModelRenderer(), level, model, state, pos, poseStack, buffers::apply, cull, state.getSeed(pos),
						OverlayTexture.NO_OVERLAY);
			} catch (VirtualMachineError e) {
				HeldThrows.end(outer);
				throw e;
			} catch (Throwable t) {
				thrown = t;
			}
			thrown = failure(HeldThrows.end(outer), thrown);
			if (thrown == null) return true;
			resetIndigoThread();
			markBroken(model, thrown, "a chunk section at " + pos, state);
			return false;
		}

		/**
		 * What a draw threw: the throw held inside a model another wraps ({@link HeldThrows}), where the model whose code
		 * threw is (what the wrapper threw after it, if anything, is suppressed in it), else what the draw's call threw.
		 */
		private static @Nullable Throwable failure(@Nullable Throwable held, @Nullable Throwable thrown) {
			if (held == null) return thrown;
			if (thrown != null && thrown != held) {
				try {
					held.addSuppressed(thrown);
				} catch (RuntimeException e) {
					// A throw suppressing itself: the held one is what counts.
				}
			}
			return held;
		}

		/**
		 * Drops this thread's Indigo context for terrain-like rendering when Indigo is the renderer: a model that threw may
		 * have left a transform pushed on its emitter, which would apply to every block the thread draws next. Indigo makes
		 * a new one when next asked. Failure path only; another renderer keeps its own state.
		 */
		private static void resetIndigoThread() {
			if (!RENDERER.getClass().getName().startsWith("net.fabricmc.fabric.impl.client.indigo.")) return;
			try {
				Object pool = Class.forName(INDIGO_TERRAIN_CONTEXT, false, RENDERER.getClass().getClassLoader()).getField("POOL").get(null);
				if (pool instanceof ThreadLocal<?> threadLocal) threadLocal.remove();
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				LOGGER.debug("Could not reset Indigo's context on {} ({})", Thread.currentThread().getName(), e.toString());
			}
		}

		/**
		 * Adds the parts made from a Fabric model's quads. False, adding nothing, for a model asked again while it emits
		 * (its {@code emitQuads} asks for its own parts): it then keeps the parts of its vanilla method.
		 */
		static boolean collectParts(BlockStateModel model, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
				List<BlockModelPart> parts) {
			Collecting collecting = COLLECTING.get();
			for (int i = 0; i < collecting.models.size(); i++) {
				if (collecting.models.get(i) == model) return false;
			}
			// Nested when a model's emitQuads asks another model for its parts (a NeoForge model wrapping a Fabric one):
			// that one gets a mesh of its own.
			boolean outermost = collecting.models.isEmpty();
			MutableMesh mesh = outermost ? collecting.mesh : RENDERER.mutableMesh();
			collecting.models.add(model);
			int partsBefore = parts.size();
			boolean failed = false;
			try {
				Throwable thrown = emitHolding(model, mesh.emitter(), level, pos, state, random);
				if (thrown == null) {
					addParts(mesh, model, parts);
				} else {
					failed = true;
					parts.subList(partsBefore, parts.size()).clear();
					markBroken(model, thrown, "the parts made from its quads at " + pos, state);
				}
			} catch (VirtualMachineError e) {
				throw e;
			} catch (Throwable t) {
				failed = true;
				parts.subList(partsBefore, parts.size()).clear();
				markBroken(model, t, "the parts made from its quads at " + pos, state);
			} finally {
				collecting.models.removeLast();
				mesh.clear();
				// The thread's mesh may hold a transform the model pushed: the next block gets a new one.
				if (failed && outermost) COLLECTING.remove();
			}
			// A model that threw keeps the parts of its vanilla method (the caller adds them).
			return !failed;
		}

		/**
		 * Emits a Fabric model's quads, holding a throw inside a model another wraps until the call returns
		 * ({@link HeldThrows}); what the call threw ({@link #failure}), or null.
		 */
		private static @Nullable Throwable emitHolding(BlockStateModel model, QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
				RandomSource random) {
			Throwable thrown = null;
			Throwable outer = HeldThrows.begin();
			try {
				// Fabric's renderer API adds FabricBlockStateModel to every block model when the game loads.
				((FabricBlockStateModel) model).emitQuads(emitter, level, pos, state, random, NO_CULLING);
			} catch (VirtualMachineError e) {
				HeldThrows.end(outer);
				throw e;
			} catch (Throwable t) {
				thrown = t;
			}
			return failure(HeldThrows.end(outer), thrown);
		}

		/** The mesh's quads as NeoForge's quads, after the item layer's (whose render type they are drawn in). */
		static void addItemQuads(MutableMesh mesh, List<BakedQuad> quads) {
			// As the block models' quads: an item model's sprites come from the block atlas, as upstream's ModelHelper has them.
			SpriteFinder sprites = ((FabricSpriteAtlasTexture) Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS)).spriteFinder();
			mesh.forEach(quad -> quads.add(withNeoForgeData(quad, quad.toBakedQuad(sprites.find(quad)))));
		}

		/** The mesh's quads as NeoForge's quads, a part for each render layer they name (null: the block's own). */
		private static void addParts(MutableMesh mesh, BlockStateModel model, List<BlockModelPart> parts) {
			// As upstream's ModelHelper.toQuadLists: block models' quads use the block atlas.
			SpriteFinder sprites = ((FabricSpriteAtlasTexture) Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS)).spriteFinder();
			Map<@Nullable ChunkSectionLayer, QuadCollection.Builder> layers = new HashMap<>();
			mesh.forEach(quad -> {
				BakedQuad baked = withNeoForgeData(quad, quad.toBakedQuad(sprites.find(quad)));
				QuadCollection.Builder builder = layers.computeIfAbsent(quad.renderLayer(), layer -> new QuadCollection.Builder());
				Direction cullFace = quad.cullFace();
				if (cullFace == null) {
					builder.addUnculledFace(baked);
				} else {
					builder.addCulledFace(cullFace, baked);
				}
			});
			// Ambient occlusion as the block has it, unless a quad turns it off (its NeoForge flag).
			layers.forEach((layer, builder) -> parts.add(new SimpleModelWrapper(builder.build(), true, model.particleIcon(), layer)));
		}

		/**
		 * The quad with what NeoForge's quads carry beyond vanilla's and upstream's {@code toBakedQuad} leaves out: its
		 * vertex colours and normals, and whether ambient occlusion is off for it ({@code TriState.TRUE} counts as the
		 * block's default, which NeoForge decides per part).
		 */
		private static BakedQuad withNeoForgeData(QuadView quad, BakedQuad baked) {
			int c0 = quad.color(0), c1 = quad.color(1), c2 = quad.color(2), c3 = quad.color(3);
			boolean white = (c0 & c1 & c2 & c3) == -1;
			boolean normals = quad.hasNormal(0) || quad.hasNormal(1) || quad.hasNormal(2) || quad.hasNormal(3);
			boolean ambientOcclusion = quad.ambientOcclusion() != TriState.FALSE;
			if (white && !normals && ambientOcclusion) return baked;
			return new BakedQuad(baked.position0(), baked.position1(), baked.position2(), baked.position3(),
					baked.packedUV0(), baked.packedUV1(), baked.packedUV2(), baked.packedUV3(),
					baked.tintIndex(), baked.direction(), baked.sprite(), baked.shade(), baked.lightEmission(),
					normals ? BakedNormals.of(normal(quad, 0), normal(quad, 1), normal(quad, 2), normal(quad, 3)) : BakedNormals.UNSPECIFIED,
					white ? BakedColors.DEFAULT : BakedColors.of(c0, c1, c2, c3),
					ambientOcclusion);
		}

		/** A vertex's normal as NeoForge packs it; 0, unspecified, when the quad has none there. */
		private static int normal(QuadView quad, int vertex) {
			return quad.hasNormal(vertex) ? BakedNormals.pack(quad.normalX(vertex), quad.normalY(vertex), quad.normalZ(vertex)) : 0;
		}

		/** One thread's models whose parts are being made, innermost last, and the mesh the outermost emits into. */
		private static final class Collecting {
			final List<BlockStateModel> models = new ArrayList<>();
			final MutableMesh mesh = RENDERER.mutableMesh();
		}
	}
}
