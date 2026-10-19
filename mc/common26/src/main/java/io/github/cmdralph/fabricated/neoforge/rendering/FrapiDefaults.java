package io.github.cmdralph.fabricated.neoforge.rendering;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModelPart;
import net.fabricmc.fabric.api.client.renderer.v1.model.ModelHelper;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric's renderer API's default {@code emitQuads}, as NeoForge's models need them (what Forgified Fabric API does).
 * Upstream's defaults collect a model's parts without the level, and give a part's quads vanilla's ambient occlusion:
 * NeoForge's models that read the level or their model data (connected textures, models of NeoForge mods) lose it, and
 * NeoForge's per-part and per-quad ambient occlusion is ignored. That matters whenever Fabric's renderer draws such a
 * model: one a Fabric model wraps, or every model when a renderer such as Sodium meshes all blocks through Fabric's
 * renderer API. Only loaded with Fabric's renderer API (fabricated-renderer.mixins.json).
 */
public final class FrapiDefaults {
	/** Leaves are drawn opaque when the player turned their transparency off (as upstream's default does). */
	private static final QuadTransform OPAQUE = quad -> {
		quad.chunkLayer(ChunkSectionLayer.SOLID);
		return true;
	};
	/**
	 * Each thread's parts, reused for every model it draws. A model's parts are those added after the ones of the models
	 * being drawn around it (a part may draw a model), and are removed when it is done.
	 */
	private static final ThreadLocal<List<BlockStateModelPart>> PARTS = ThreadLocal.withInitial(ArrayList::new);

	private FrapiDefaults() {
	}

	/** {@code FabricBlockStateModel.emitQuads}, with the parts NeoForge's level-aware {@code collectParts} gives. */
	public static void emitQuads(BlockStateModel model, QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest) {
		boolean opaque = ModelBlockRenderer.forceOpaque(Minecraft.getInstance().options.cutoutLeaves().get(), state);
		if (opaque) emitter.pushTransform(OPAQUE);
		List<BlockStateModelPart> parts = PARTS.get();
		int first = parts.size();
		try {
			model.collectParts(level, pos, state, random, parts);
			// Fabric's renderer API makes every part a FabricBlockStateModelPart when the game runs.
			for (int i = first; i < parts.size(); i++) ((FabricBlockStateModelPart) parts.get(i)).emitQuads(emitter, cullTest);
		} finally {
			parts.subList(first, parts.size()).clear();
			if (opaque) emitter.popTransform();
		}
	}

	/** {@code FabricBlockStateModelPart.emitQuads}, with NeoForge's ambient occlusion: the part's, unless a quad turns it off. */
	public static void emitQuads(BlockStateModelPart part, QuadEmitter emitter, Predicate<@Nullable Direction> cullTest) {
		TriState ambientOcclusion = switch (part.ambientOcclusion()) {
			case TRUE -> TriState.TRUE;
			case FALSE -> TriState.FALSE;
			case DEFAULT -> TriState.DEFAULT;
		};
		for (int i = 0; i <= ModelHelper.NULL_FACE_ID; i++) {
			Direction cullFace = ModelHelper.faceFromIndex(i);
			if (cullTest.test(cullFace)) continue;
			List<BakedQuad> quads = part.getQuads(cullFace);
			for (int j = 0; j < quads.size(); j++) {
				BakedQuad quad = quads.get(j);
				emitter.cullFace(cullFace);
				emitter.fromBakedQuad(quad);
				emitter.ambientOcclusion(quad.materialInfo().ambientOcclusion() ? ambientOcclusion : TriState.FALSE);
				emitter.shadeMode(ShadeMode.VANILLA);
				emitter.emit();
			}
		}
	}
}
