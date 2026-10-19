package io.github.cmdralph.fabricated.neoforge.rendering;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.vertex.BufferBuilder;

import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.render.AltModelBlockRenderer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the block models only Fabric's renderer API can draw into NeoForge's chunk meshes.
 *
 * <p>A model a Fabric mod implements itself (its own {@code emitQuads}) has no geometry NeoForge's block renderer can
 * see, so it was invisible in the world. Upstream sends every block in the world through Fabric's renderer; on NeoForge
 * that would lose what NeoForge adds to block rendering (level-aware model parts, dynamic tints, face culling and
 * ambient occlusion per part). So only blocks whose model is a Fabric model go through Fabric's renderer (Indigo, or
 * whichever renderer is registered), into the section's own per-layer buffers; every other block keeps NeoForge's path.
 *
 * <p>Sections compile on several worker threads at once: the state here is immutable, or confined to one thread.
 */
public final class FabricTerrain {
	/** Fabric's renderer API is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-renderer-api-v1");

	private FabricTerrain() {
	}

	/** The section compiler's per-layer buffers, which Fabricated's mixin exposes. */
	public interface Layers {
		BufferBuilder fabricated$layer(Map<ChunkSectionLayer, BufferBuilder> startedLayers, SectionBufferBuilderPack builders, ChunkSectionLayer layer);
	}

	/** Whether this block model is drawn by Fabric's renderer instead of NeoForge's. */
	public static boolean draws(BlockStateModel model) {
		return ENABLED && Frapi.isFabricModel(model);
	}

	/** Draws one block through Fabric's renderer, as NeoForge's block renderer would have with the same arguments. */
	public static void tesselate(Layers layers, Map<ChunkSectionLayer, BufferBuilder> startedLayers, SectionBufferBuilderPack builders,
			boolean ambientOcclusion, BlockColors blockColors,
			float x, float y, float z, BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed) {
		Frapi.tesselate(layers, startedLayers, builders, ambientOcclusion, blockColors, x, y, z, level, pos, state, model, seed);
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
				}
				boolean fabricModel = owner != FabricBlockStateModel.class && !owner.getName().startsWith("net.minecraft.");
				if (fabricModel) LOGGER.debug("Block models of class {} are drawn with Fabric's renderer", type.getName());
				return fabricModel;
			}
		};
		/** Each thread's renderer and emitter, reused for every block it draws. */
		private static final ThreadLocal<WorkerState> STATE = new ThreadLocal<>();

		private static @Nullable Renderer findRenderer() {
			try {
				return Renderer.get();
			} catch (UnsupportedOperationException e) {
				LOGGER.warn("Fabric's renderer API has no renderer registered: Fabric block models stay invisible in the world", e);
				return null;
			}
		}

		static boolean isFabricModel(BlockStateModel model) {
			return RENDERER != null && FABRIC_MODEL.get(model.getClass());
		}

		static void tesselate(Layers layers, Map<ChunkSectionLayer, BufferBuilder> startedLayers, SectionBufferBuilderPack builders,
				boolean ambientOcclusion, BlockColors blockColors,
				float x, float y, float z, BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed) {
			WorkerState worker = STATE.get();
			if (worker == null || worker.ambientOcclusion != ambientOcclusion || worker.blockColors != blockColors) {
				// Once per thread, and again when the smooth lighting option changes (the game makes a new compiler).
				worker = new WorkerState(RENDERER, ambientOcclusion, blockColors);
				STATE.set(worker);
			}
			worker.layers = layers;
			worker.startedLayers = startedLayers;
			worker.builders = builders;
			try {
				worker.renderer.tesselateBlock(worker.emitter, x, y, z, level, pos, state, model, seed);
			} catch (Throwable t) {
				CrashReport report = CrashReport.forThrowable(t, "Tesselating block model with Fabric's renderer");
				CrashReportCategory category = report.addCategory("Block model being tesselated");
				category.setDetail("Block", state);
				category.setDetail("Position", pos);
				category.setDetail("Model class", model.getClass().getName());
				throw new ReportedException(report);
			} finally {
				// The section's buffers are only reachable while it compiles.
				worker.layers = null;
				worker.startedLayers = null;
				worker.builders = null;
			}
		}
	}

	/**
	 * One thread's Fabric block renderer and the emitter it outputs to. The renderer (Indigo's keeps the thread's light
	 * cache) and its emitter are confined to the thread that made them; the section being compiled is set per block.
	 */
	private static final class WorkerState implements Consumer<MutableQuadView> {
		final boolean ambientOcclusion;
		final BlockColors blockColors;
		final AltModelBlockRenderer renderer;
		final QuadEmitter emitter;
		@Nullable Layers layers;
		@Nullable Map<ChunkSectionLayer, BufferBuilder> startedLayers;
		@Nullable SectionBufferBuilderPack builders;

		WorkerState(Renderer renderer, boolean ambientOcclusion, BlockColors blockColors) {
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
}
