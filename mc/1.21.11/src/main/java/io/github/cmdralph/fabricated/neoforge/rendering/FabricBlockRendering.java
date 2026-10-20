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

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.fabricmc.fabric.api.renderer.v1.sprite.FabricSpriteAtlasTexture;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
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
		Frapi.tesselate(dispatcher, level, model, state, pos, poseStack, buffers, cull);
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

	private static ReportedException crash(Throwable t, String what, BlockState state, BlockPos pos, BlockStateModel model) {
		CrashReport report = CrashReport.forThrowable(t, what);
		CrashReportCategory category = report.addCategory("Block model being drawn");
		category.setDetail("Block", state);
		category.setDetail("Position", pos);
		category.setDetail("Model class", model.getClass().getName());
		return new ReportedException(report);
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

		static boolean isFabricModel(BlockStateModel model) {
			return RENDERER != null && (FABRIC_MODEL.get(model.getClass()) || model instanceof Composite composite && composite.fabricated$hasFabricPart());
		}

		static void tesselate(BlockRenderDispatcher dispatcher, BlockAndTintGetter level, BlockStateModel model, BlockState state, BlockPos pos,
				PoseStack poseStack, Function<ChunkSectionLayer, VertexConsumer> buffers, boolean cull) {
			try {
				RENDERER.render(dispatcher.getModelRenderer(), level, model, state, pos, poseStack, buffers::apply, cull, state.getSeed(pos),
						OverlayTexture.NO_OVERLAY);
			} catch (VirtualMachineError | ReportedException e) {
				throw e;
			} catch (Throwable t) {
				throw crash(t, "Tesselating block model with Fabric's renderer", state, pos, model);
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
			MutableMesh mesh = collecting.models.isEmpty() ? collecting.mesh : RENDERER.mutableMesh();
			collecting.models.add(model);
			try {
				// Fabric's renderer API adds FabricBlockStateModel to every block model when the game loads.
				((FabricBlockStateModel) model).emitQuads(mesh.emitter(), level, pos, state, random, NO_CULLING);
				addParts(mesh, model, parts);
			} catch (VirtualMachineError | ReportedException e) {
				throw e;
			} catch (Throwable t) {
				throw crash(t, "Making block model parts from a Fabric model's quads", state, pos, model);
			} finally {
				collecting.models.removeLast();
				mesh.clear();
			}
			return true;
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
