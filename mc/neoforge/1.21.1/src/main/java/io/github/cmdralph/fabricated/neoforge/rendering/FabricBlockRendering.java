package io.github.cmdralph.fabricated.neoforge.rendering;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderContext;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.TerrainRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Lets NeoForge 21.1's renderers draw the block models only Fabric's renderer API can draw, through Fabric API's own
 * renderer (Indigo), as upstream draws them (FRAPI-5).
 *
 * <p>A Fabric model (one that says it is no vanilla adapter) has the quads it emits, and NeoForge's block renderer asks
 * it for vanilla's, which such a model usually leaves empty: it was invisible. Indigo's own hooks miss NeoForge's code:
 * its chunk section hook redirects vanilla's call, which NeoForge replaced (pre-flight leaves it out), and its block
 * renderer hook is on vanilla's overload, which NeoForge's renderers no longer call. So:
 * <ul>
 * <li>in the chunk sections NeoForge compiles, a block whose model is a Fabric model, or a vanilla multipart or weighted
 * model with one among its parts, is drawn once (at its first render type) by Indigo's terrain context, into the
 * section's buffer for each quad's layer, as upstream draws it; every other block keeps NeoForge's path;</li>
 * <li>where NeoForge's renderers draw one block into the buffer they give (moving blocks, falling or pushed by a piston,
 * the breaking overlay, and blocks drawn through the block renderer's {@code renderBatched}), Indigo's block context
 * draws it once (at its first render type), into that buffer, as upstream draws it into the one buffer it is given.</li>
 * </ul>
 *
 * <p>A Fabric model that throws there fails alone (FRAPI rule 8, {@link FrapiFailures}): the block is drawn by NeoForge
 * instead, from the quads the model gives NeoForge, as is every model of its class from then on.
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

	/** The section compiler (its mixin): the section's buffer for a layer, begun the first time it is asked for. */
	public interface SectionLayers {
		BufferBuilder fabricated$layer(Map<RenderType, BufferBuilder> layers, SectionBufferBuilderPack pack, RenderType layer);
	}

	/** Whether this block model is drawn by Fabric's renderer instead of NeoForge's. */
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
	 * Draws one block of a chunk section whose model {@link #draws} with Fabric's renderer, for the render type NeoForge
	 * is drawing: the whole block at the first, nothing at the others (its quads went to their layers' buffers then).
	 * False if the model threw: NeoForge's renderer then draws the block (quads already sent stay in this build of the
	 * section).
	 *
	 * @param layers the compile's buffers by layer, which tell one compile from the next
	 */
	public static boolean tesselate(SectionLayers compiler, Map<RenderType, BufferBuilder> layers, SectionBufferBuilderPack pack, RenderChunkRegion region,
			BlockState state, BlockPos pos, PoseStack poseStack, BakedModel model) {
		return Indigo.tesselate(compiler, layers, pack, region, state, pos, poseStack, model);
	}

	/** A section's compile ended: this thread's terrain context lets go of it. */
	public static void endSection() {
		if (ENABLED && Frapi.INDIGO) Indigo.endSection();
	}

	/**
	 * Draws a block whose model {@link #draws} with Fabric's renderer into the buffer NeoForge's renderer gives, as its
	 * {@code tesselateBlock} would have with the same arguments: the whole block at the model's first render type (or
	 * with no type, or one the model doesn't have), nothing at its others. False if the model threw: NeoForge's renderer
	 * then draws the block.
	 *
	 * @param where where the block is drawn, for the log ("a moving block", ...)
	 */
	public static boolean tesselateBlock(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, PoseStack poseStack, VertexConsumer buffer,
			boolean cull, RandomSource random, long seed, int overlay, ModelData modelData, @Nullable RenderType renderType, String where) {
		if (renderType != null && !firstType(model, state, seed, modelData, renderType)) return true;
		return Indigo.tesselateBlock(level, model, state, pos, poseStack, buffer, cull, random, seed, overlay, where);
	}

	/**
	 * Whether NeoForge's renderer draws the model's first render type now, or one the model doesn't have (a caller's own):
	 * the call Fabric's renderer draws the block at. Asked with a random seeded as the callers seed theirs for the types.
	 */
	private static boolean firstType(BakedModel model, BlockState state, long seed, ModelData modelData, RenderType renderType) {
		ChunkRenderTypeSet types = model.getRenderTypes(state, RandomSource.create(seed), modelData);
		Iterator<RenderType> iterator = types.iterator();
		return !iterator.hasNext() || iterator.next() == renderType || !types.contains(renderType);
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
			LOGGER.info("Fabric block models are drawn by Fabric API's renderer (Indigo), through Fabricated's hooks into NeoForge's renderers");
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
		 * Marks what threw broken ({@link FrapiFailures#markBroken}): the model's class if it is a Fabric model; in a
		 * composite, its Fabric parts the stack trace shows emitting, or all of them if it shows none.
		 */
		static void markBroken(BakedModel model, Throwable error, String where, BlockState state) {
			List<BakedModel> fabricModels = new ArrayList<>();
			fabricModels(model, fabricModels, 0);
			boolean marked = false;
			for (BakedModel fabricModel : fabricModels) {
				FrapiFailures.ModelClass type = MODEL_CLASSES.get(fabricModel.getClass());
				if (FrapiFailures.threwIn(error, type, EMIT)) {
					FrapiFailures.markBroken(fabricModel.getClass(), type, error, where, state);
					marked = true;
				}
			}
			if (marked) return;
			for (BakedModel fabricModel : fabricModels) {
				FrapiFailures.markBroken(fabricModel.getClass(), MODEL_CLASSES.get(fabricModel.getClass()), error, where, state);
			}
			if (fabricModels.isEmpty()) FrapiFailures.unattributed(model.getClass(), error, where, state);
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
		/** Each section worker thread's terrain context, and the compile it is prepared for. */
		private static final ThreadLocal<Section> SECTION = ThreadLocal.withInitial(Section::new);
		/** Each thread's context for blocks drawn outside chunk sections. */
		private static final ThreadLocal<BlockRenderContext> BLOCK = ThreadLocal.withInitial(BlockRenderContext::new);

		static boolean tesselate(SectionLayers compiler, Map<RenderType, BufferBuilder> layers, SectionBufferBuilderPack pack, RenderChunkRegion region,
				BlockState state, BlockPos pos, PoseStack poseStack, BakedModel model) {
			Section section = SECTION.get();
			long block = pos.asLong();
			if (section.layers != layers) {
				// A new compile: Indigo begins a layer's buffer as the compiler does, when a quad first needs it.
				section.context.prepare(region, layer -> compiler.fabricated$layer(layers, pack, layer));
				section.layers = layers;
			} else if (section.block == block) {
				return true; // the block's next render type: Fabric's renderer drew the whole block at its first
			}
			section.block = block;
			try {
				section.context.tessellateBlock(state, pos, model, poseStack);
				return true;
			} catch (VirtualMachineError e) {
				throw e;
			} catch (Throwable t) {
				// The context may hold a transform the model pushed: this thread gets a new one, prepared at its next block.
				SECTION.remove();
				untranslate(poseStack, state, region, pos);
				Frapi.markBroken(model, t, "a chunk section at " + pos, state);
				return false;
			}
		}

		static void endSection() {
			Section section = SECTION.get();
			if (section.layers == null) return;
			section.context.release();
			section.layers = null;
		}

		static boolean tesselateBlock(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, PoseStack poseStack, VertexConsumer buffer,
				boolean cull, RandomSource random, long seed, int overlay, String where) {
			try {
				BLOCK.get().render(level, model, state, pos, poseStack, buffer, cull, random, seed, overlay);
				return true;
			} catch (VirtualMachineError e) {
				throw e;
			} catch (Throwable t) {
				BLOCK.remove(); // it may hold a transform the model pushed
				untranslate(poseStack, state, level, pos);
				Frapi.markBroken(model, t, where + " at " + pos, state);
				return false;
			}
		}

		/**
		 * Takes back the block's offset, which Indigo applies to the pose first, so NeoForge's renderer, applying it again,
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

		/** A thread's terrain context, the compile it is prepared for (its buffers by layer) and the last block it drew. */
		private static final class Section {
			final TerrainRenderContext context = new TerrainRenderContext();
			@Nullable Map<RenderType, BufferBuilder> layers;
			long block;
		}
	}
}
