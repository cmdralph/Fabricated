package io.github.cmdralph.fabricated.forge.rendering;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.impl.client.indigo.renderer.accessor.AccessChunkRendererRegion;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderContext;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.TerrainRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Lets Forge 47's renderers draw the block models only Fabric's renderer API can draw, through Fabric API's own renderer
 * (Indigo), as upstream draws them on 1.20.1 (task 4, R5; as FRAPI-5 does on NeoForge 21.1).
 *
 * <p>A Fabric model (one that says it is no vanilla adapter) has the quads it emits, and Forge's block renderer asks it
 * for vanilla's, which such a model usually leaves empty: it was invisible. Indigo's own hooks miss Forge's code: its
 * chunk section hook redirects vanilla's {@code renderBatched} call, which Forge replaced with one taking model data and
 * a render type (pre-flight leaves that redirect out, and keeps the hook that prepares Indigo's terrain context for each
 * compile), and its block renderer hook is on vanilla's {@code tesselateBlock}, which Forge's renderers no longer call.
 * So:
 * <ul>
 * <li>in the chunk sections Forge compiles, a block whose model is a Fabric model, or a vanilla multipart or weighted
 * model with one among its parts, is drawn once (at its first render type) by the terrain context Indigo's hook
 * prepared for the compile, into the section's buffer for each quad's layer, as upstream's redirect draws it; every
 * other block keeps Forge's path;</li>
 * <li>where Forge's renderers draw one block into the buffer they give (moving blocks, falling or pushed by a piston,
 * the breaking overlay, and blocks drawn through the block renderer's {@code renderBatched}), Indigo's block context
 * draws it once (at its first render type), into that buffer, as upstream draws it into the one buffer it is given.</li>
 * </ul>
 * Blocks drawn as entities (a minecart's, an enderman's) and items are as upstream draws them on 1.20.1: items through
 * Indigo's own item hook, which applies on Forge 47, blocks drawn as entities from their vanilla quads.
 *
 * <p>A Fabric model that throws there fails alone (FRAPI rule 8, {@link FrapiFailures}): the block is drawn by Forge
 * instead, at each of its render types, from the quads the model gives Forge, as is every model of the class that threw
 * from then on (not of a class wrapping it: {@link FrapiFailures#putDown}).
 *
 * <p>Sections compile on several worker threads at once: the state here is immutable or per thread.
 */
public final class FabricBlockRendering {
	/** Fabric's renderer API is installed. Its classes are only touched then; without it no block is looked at. */
	public static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-renderer-api-v1");

	private FabricBlockRendering() {
	}

	/**
	 * A vanilla model made of other models (multipart, weighted), which Fabricated's mixins let say whether one of them is
	 * drawn by Fabric's renderer, or broken. The answer is kept in the model, which a resource reload replaces, for one
	 * {@link FrapiFailures#generation()}. (Upstream's own answer, {@code isVanillaAdapter}, is kept from its construction,
	 * so it can't tell a broken part.)
	 */
	public interface Composite {
		/** {@link FrapiFailures#NONE}, {@link FrapiFailures#FABRIC} or {@link FrapiFailures#BROKEN}, from its parts' states. */
		int fabricated$state();

		/** The models it is made of (any of them). Asked on the failure path only. */
		List<BakedModel> fabricated$parts();
	}

	/** Whether this block model is drawn by Fabric's renderer instead of Forge's. */
	public static boolean draws(BakedModel model) {
		return state(model) == FrapiFailures.FABRIC;
	}

	/** Whether Fabric's renderer draws this model, or would if drawing it hadn't thrown ({@link FrapiFailures}). */
	public static int state(BakedModel model) {
		return ENABLED ? Frapi.state(model) : FrapiFailures.NONE;
	}

	/** A composite model's state, from one of its parts' and the state of those before it. */
	public static int combine(int state, BakedModel part) {
		return state == FrapiFailures.BROKEN ? state : FrapiFailures.combine(state, state(part));
	}

	/**
	 * Draws one block of a chunk section whose model {@link #draws} with Fabric's renderer, for the render type Forge is
	 * drawing: the whole block at the first, nothing at the others (its quads went to their layers' buffers then). False
	 * if Forge's renderer draws the block instead, at that render type and at its others in this compile: the model threw
	 * (even when what threw isn't marked: a model it wraps, {@link FrapiFailures#putDown}; quads already sent stay in this
	 * build of the section), or Indigo's hook prepared no terrain context for the compile (logged once).
	 */
	public static boolean tesselate(RenderChunkRegion region, BlockState state, BlockPos pos, PoseStack poseStack, BakedModel model) {
		return Indigo.tesselate(region, state, pos, poseStack, model);
	}

	/** A section's compile ended: this thread lets go of its region. */
	public static void endSection() {
		if (ENABLED && Frapi.INDIGO) Indigo.endSection();
	}

	/**
	 * Draws a block whose model {@link #draws} with Fabric's renderer into the buffer Forge's renderer gives, as its
	 * {@code tesselateBlock} would have with the same arguments: the whole block at the model's first render type (or
	 * with no type, or one the model doesn't have), nothing at its others. False if the model threw: Forge's renderer then
	 * draws the block, at that render type and at its others. False too for a chunk section's block Forge's renderer draws
	 * ({@link #tesselate}): the section compiler's own call then reaches here.
	 *
	 * @param renderType the type Forge draws, which its moving block renderers give as the moving block type of the
	 *                   model's ({@code RenderTypeHelper.getMovingBlockRenderType})
	 * @param where      where the block is drawn, for the log ("a falling block", ...)
	 */
	public static boolean tesselateBlock(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, PoseStack poseStack, VertexConsumer buffer,
			boolean cull, RandomSource random, long seed, int overlay, ModelData modelData, @Nullable RenderType renderType, String where) {
		if (level instanceof RenderChunkRegion && Indigo.sectionDrawnByForge(pos)) return false;
		if (renderType != null && !firstType(model, state, seed, modelData, renderType)) return !Indigo.fellBack(state, pos);
		return Indigo.tesselateBlock(level, model, state, pos, poseStack, buffer, cull, random, seed, overlay, where);
	}

	/**
	 * Whether Forge's renderer draws the model's first render type now (or its moving block type), or one the model
	 * doesn't have (a caller's own): the call Fabric's renderer draws the block at. Asked with a random seeded as the
	 * callers seed theirs for the types.
	 */
	private static boolean firstType(BakedModel model, BlockState state, long seed, ModelData modelData, RenderType renderType) {
		ChunkRenderTypeSet types = model.getRenderTypes(state, RandomSource.create(seed), modelData);
		boolean first = true;
		for (RenderType type : types) {
			if (type == renderType || RenderTypeHelper.getMovingBlockRenderType(type) == renderType) return first;
			first = false;
		}
		return true;
	}

	/** Everything that names Fabric's renderer API: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Frapi {
		private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
		/** Fabric's block model method, the frame a model's throw shows. */
		static final String EMIT = "emitBlockQuads";
		/**
		 * Whether Fabric API's renderer (Indigo) is the registered one, and Fabric's renderer API added its model
		 * interface to every block model: then Fabricated's hooks draw Fabric models. Another renderer has its own hooks.
		 * Resolved once, when the first block is drawn: renderers register during client initialization (Indigo in its
		 * client entrypoint), before any world renders.
		 */
		static final boolean INDIGO = findIndigo();
		/** For a model class that says it is a Fabric model: the class declaring its emitter, and whether that threw. */
		private static final ClassValue<FrapiFailures.ModelClass> MODEL_CLASSES = new ClassValue<>() {
			@Override
			protected FrapiFailures.ModelClass computeValue(Class<?> type) {
				String emitter;
				try {
					emitter = type.getMethod(EMIT, BlockAndTintGetter.class, BlockState.class, BlockPos.class, Supplier.class, RenderContext.class)
							.getDeclaringClass().getName();
				} catch (NoSuchMethodException | LinkageError e) {
					// getMethod resolves the types every public method names: one from a mod that isn't installed throws. Still
					// drawn by Fabric's renderer, which doesn't look the method up; a throw is then put down to the model itself.
					emitter = null;
				}
				LOGGER.debug("Block models of class {} are drawn with Fabric's renderer", type.getName());
				return new FrapiFailures.ModelClass(true, emitter);
			}
		};

		private static boolean findIndigo() {
			if (!FabricBakedModel.class.isAssignableFrom(BakedModel.class)) {
				LOGGER.warn("Fabric's renderer API didn't add its model interface to block models: Fabric block models stay invisible in the world");
				return false;
			}
			Renderer renderer = RendererAccess.INSTANCE.getRenderer();
			if (renderer == null) {
				LOGGER.warn("Fabric's renderer API has no renderer registered: Fabric block models stay invisible in the world");
				return false;
			}
			if (!renderer.getClass().getName().startsWith("net.fabricmc.fabric.impl.client.indigo.")) {
				LOGGER.info("Fabric block models are drawn by the renderer {}, through its own hooks", renderer.getClass().getName());
				return false;
			}
			LOGGER.info("Fabric block models are drawn by Fabric API's renderer (Indigo), through Fabricated's hooks into Forge's renderers");
			return true;
		}

		static int state(BakedModel model) {
			if (!INDIGO) return FrapiFailures.NONE;
			// First: upstream says a composite with a Fabric part is no vanilla adapter, and can't tell a broken part.
			if (model instanceof Composite composite) return composite.fabricated$state();
			if (((FabricBakedModel) model).isVanillaAdapter()) return FrapiFailures.NONE;
			return MODEL_CLASSES.get(model.getClass()).broken() ? FrapiFailures.BROKEN : FrapiFailures.FABRIC;
		}

		/**
		 * Marks what threw broken: the model's class if it is a Fabric model, or its Fabric parts in a composite, as the
		 * stack trace puts the throw down to them ({@link FrapiFailures#putDown}: not a wrapper, when a model it wraps threw).
		 */
		static void markBroken(BakedModel model, Throwable error, String where, BlockState state) {
			List<BakedModel> fabricModels = new ArrayList<>();
			fabricModels(model, fabricModels, 0);
			FrapiFailures.putDown(model, fabricModels, MODEL_CLASSES, EMIT, error, where, state);
		}

		/** The Fabric models a model is, or is made of (through nested composites, to a depth no vanilla model reaches). */
		private static void fabricModels(BakedModel model, List<BakedModel> out, int depth) {
			if (model instanceof Composite composite) {
				if (depth >= 8) return;
				List<BakedModel> parts;
				try {
					parts = composite.fabricated$parts();
				} catch (RuntimeException | LinkageError e) {
					return; // parts that can't be listed: the Fabric models found elsewhere are marked
				}
				for (BakedModel part : parts) fabricModels(part, out, depth + 1);
				return;
			}
			boolean fabric;
			try {
				fabric = !((FabricBakedModel) model).isVanillaAdapter();
			} catch (RuntimeException | LinkageError e) {
				fabric = true; // a model that throws saying what it is: put the failure down to it
			}
			if (fabric) out.add(model);
		}
	}

	/** Everything that names Indigo's classes: loaded only once a model was found drawn by Fabric's renderer (Indigo). */
	private static final class Indigo {
		private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
		/** Each section worker thread's compile: its region, the last block drawn and whether Forge's renderer draws it. */
		private static final ThreadLocal<Section> SECTION = ThreadLocal.withInitial(Section::new);
		/** Each thread's context for blocks drawn outside chunk sections, and the last of them that threw. */
		private static final ThreadLocal<Single> BLOCK = ThreadLocal.withInitial(Single::new);
		/** Whether a compile without the terrain context Indigo's hook prepares was logged. */
		private static final AtomicBoolean UNPREPARED = new AtomicBoolean();

		static boolean tesselate(RenderChunkRegion region, BlockState state, BlockPos pos, PoseStack poseStack, BakedModel model) {
			Section section = SECTION.get();
			long block = pos.asLong();
			if (section.region != region) {
				// A new compile: the terrain context Indigo's chunk build hook prepared for it (upstream's redirect draws with
				// the same one), which its hook lets go of when the compile's blocks are done.
				section.region = region;
				section.context = region instanceof AccessChunkRendererRegion access ? access.fabric_getRenderer() : null;
				if (section.context == null && !UNPREPARED.getAndSet(true)) {
					LOGGER.warn("Indigo's chunk build hook prepared no terrain context for a chunk section Forge compiles: Fabric block models there are"
							+ " drawn by Forge's renderer, from the quads they give it (often none)");
				}
			} else if (section.block == block) {
				// The block's next render type: Fabric's renderer drew the whole block at its first, or Forge's draws each.
				return !section.drawnByForge;
			}
			section.block = block;
			section.drawnByForge = false;
			TerrainRenderContext context = section.context;
			if (context == null) {
				section.drawnByForge = true;
				return false;
			}
			try {
				context.tessellateBlock(state, pos, model, poseStack);
				return true;
			} catch (VirtualMachineError e) {
				throw e;
			} catch (Throwable t) {
				// The context is the compile's, kept for its other blocks: the transforms the model left pushed come off it.
				popTransforms(context);
				section.drawnByForge = true;
				untranslate(poseStack, state, region, pos);
				Frapi.markBroken(model, t, "a chunk section at " + pos, state);
				return false;
			}
		}

		/**
		 * Whether Forge's renderer draws the block at {@code pos} in this thread's chunk section compile (its model threw,
		 * or there was no terrain context): the compiler's own call ({@code renderBatched}) is then Forge's renderer drawing
		 * it, not Fabric's.
		 */
		static boolean sectionDrawnByForge(BlockPos pos) {
			Section section = SECTION.get();
			return section.drawnByForge && section.region != null && section.block == pos.asLong();
		}

		static void endSection() {
			Section section = SECTION.get();
			section.region = null;
			section.context = null;
			section.drawnByForge = false;
		}

		static boolean tesselateBlock(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, PoseStack poseStack, VertexConsumer buffer,
				boolean cull, RandomSource random, long seed, int overlay, String where) {
			Single single = BLOCK.get();
			single.fellBack = null;
			try {
				single.context.render(level, model, state, pos, poseStack, buffer, cull, random, seed, overlay);
				return true;
			} catch (VirtualMachineError e) {
				throw e;
			} catch (Throwable t) {
				single.context = new BlockRenderContext(); // it may hold a transform the model pushed
				single.fellBack = state;
				single.fellBackPos = pos.asLong();
				untranslate(poseStack, state, level, pos);
				Frapi.markBroken(model, t, where + " at " + pos, state);
				return false;
			}
		}

		/**
		 * Whether the block (its state, where it is) is the one whose model last threw at its first render type on this
		 * thread: Forge's renderer then draws its other render types too.
		 */
		static boolean fellBack(BlockState state, BlockPos pos) {
			Single single = BLOCK.get();
			return single.fellBack == state && single.fellBackPos == pos.asLong();
		}

		/** Pops the transforms a model that threw left pushed on the compile's context (none are between two blocks). */
		private static void popTransforms(RenderContext context) {
			try {
				for (int i = 0; i < 64 && context.hasTransform(); i++) context.popTransform();
			} catch (RuntimeException e) {
				LOGGER.warn("Could not reset Indigo's terrain context after a block model threw: later blocks of the section may be transformed wrongly", e);
			}
		}

		/**
		 * Takes back the block's offset, which Indigo applies to the pose first, so Forge's renderer, applying it again,
		 * draws the block where it is. If working the offset out throws, so did Indigo's, before applying it. Failure path.
		 */
		private static void untranslate(PoseStack poseStack, BlockState state, BlockAndTintGetter level, BlockPos pos) {
			Vec3 offset;
			try {
				offset = state.getOffset(level, pos);
			} catch (RuntimeException e) {
				return;
			}
			poseStack.translate(-offset.x, -offset.y, -offset.z);
		}

		/**
		 * A thread's compile (its region, which Indigo's hook gave the terrain context), the last block it drew and whether
		 * Forge's renderer draws that one.
		 */
		private static final class Section {
			@Nullable RenderChunkRegion region;
			@Nullable TerrainRenderContext context;
			long block;
			boolean drawnByForge;
		}

		/**
		 * A thread's block context, and the last block (its state, where it is) whose model threw at its first render
		 * type there; a block state lives as long as the game, unlike a model, which a resource reload replaces.
		 */
		private static final class Single {
			BlockRenderContext context = new BlockRenderContext();
			@Nullable BlockState fellBack;
			long fellBackPos;
		}
	}
}
