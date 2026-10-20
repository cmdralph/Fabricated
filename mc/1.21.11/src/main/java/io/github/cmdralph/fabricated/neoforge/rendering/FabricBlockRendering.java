package io.github.cmdralph.fabricated.neoforge.rendering;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the block models only Fabric's renderer API can draw in chunk sections, where NeoForge draws blocks (1.21.11).
 *
 * <p>A model a Fabric mod implements itself (its own {@code emitQuads}) has no geometry NeoForge's block renderer can
 * see, so it was invisible. Upstream sends every block through Fabric's renderer; on NeoForge that would lose what
 * NeoForge adds to block rendering (level-aware model parts, per-part render layers). So only a block whose model is a
 * Fabric model, or a vanilla multipart or weighted model with a Fabric model among its parts, goes through Fabric's
 * renderer (Indigo, or whichever renderer is registered), into NeoForge's buffer for each quad's layer; every other block
 * keeps NeoForge's path. The same goes for moving blocks (pushed by a piston, falling); the 26.x counterpart also covers
 * the breaking overlay.
 *
 * <p>Sections compile on several worker threads at once: the state here is immutable, and the renderer keeps its own
 * per-thread state.
 */
public final class FabricBlockRendering {
	/** Fabric's renderer API is installed. Its classes are only touched then; without it no block is looked at. */
	public static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-renderer-api-v1");

	private FabricBlockRendering() {
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
	public static void tesselate(BlockRenderDispatcher dispatcher, BlockAndTintGetter level, BlockStateModel model, BlockState state, BlockPos pos,
			PoseStack poseStack, Function<ChunkSectionLayer, VertexConsumer> buffers, boolean cull) {
		tesselate(dispatcher, level, model, state, pos, poseStack, buffers, cull, state.getSeed(pos));
	}

	/**
	 * Draws one block with the given random seed: a moving block's comes from where it started moving, as NeoForge's
	 * renderer has it.
	 */
	public static void tesselate(BlockRenderDispatcher dispatcher, BlockAndTintGetter level, BlockStateModel model, BlockState state, BlockPos pos,
			PoseStack poseStack, Function<ChunkSectionLayer, VertexConsumer> buffers, boolean cull, long seed) {
		Frapi.tesselate(dispatcher, level, model, state, pos, poseStack, buffers, cull, seed);
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

		static void tesselate(BlockRenderDispatcher dispatcher, BlockAndTintGetter level, BlockStateModel model, BlockState state, BlockPos pos,
				PoseStack poseStack, Function<ChunkSectionLayer, VertexConsumer> buffers, boolean cull, long seed) {
			try {
				RENDERER.render(dispatcher.getModelRenderer(), level, model, state, pos, poseStack, buffers::apply, cull, seed, OverlayTexture.NO_OVERLAY);
			} catch (VirtualMachineError | ReportedException e) {
				throw e;
			} catch (Throwable t) {
				CrashReport report = CrashReport.forThrowable(t, "Tesselating block model with Fabric's renderer");
				CrashReportCategory category = report.addCategory("Block model being drawn");
				category.setDetail("Block", state);
				category.setDetail("Position", pos);
				category.setDetail("Model class", model.getClass().getName());
				throw new ReportedException(report);
			}
		}
	}
}
