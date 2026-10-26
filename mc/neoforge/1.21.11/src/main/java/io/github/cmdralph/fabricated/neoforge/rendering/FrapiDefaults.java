package io.github.cmdralph.fabricated.neoforge.rendering;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockModelPart;
import net.fabricmc.fabric.api.renderer.v1.model.ModelHelper;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import net.neoforged.neoforge.client.model.quad.BakedNormals;

/**
 * Fabric's renderer API's default {@code emitQuads}, as NeoForge's models need them (1.21.11; the 26.x counterpart has
 * the same rules). Upstream's defaults collect a model's parts without the level, and give a part's quads vanilla's
 * ambient occlusion: NeoForge's models that read the level or their model data (connected textures, models of NeoForge
 * mods) lose it, and NeoForge's per-part and per-quad ambient occlusion is ignored. That matters whenever Fabric's
 * renderer draws such a model, as the one a Fabric model wraps. Only loaded with Fabric's renderer API
 * (fabricated-renderer.mixins.json).
 */
public final class FrapiDefaults {
	/**
	 * Each thread's parts, reused for every model it draws. A model's parts are those added after the ones of the models
	 * being drawn around it (a part may draw a model), and are removed when it is done.
	 */
	private static final ThreadLocal<List<BlockModelPart>> PARTS = ThreadLocal.withInitial(ArrayList::new);

	private FrapiDefaults() {
	}

	/** {@code FabricBlockStateModel.emitQuads}, with the parts NeoForge's level-aware {@code collectParts} gives. */
	public static void emitQuads(BlockStateModel model, QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest) {
		List<BlockModelPart> parts = PARTS.get();
		int first = parts.size();
		try {
			model.collectParts(level, pos, state, random, parts);
			// Fabric's renderer API makes every part a FabricBlockModelPart when the game runs.
			for (int i = first; i < parts.size(); i++) ((FabricBlockModelPart) parts.get(i)).emitQuads(emitter, cullTest);
		} finally {
			// Not subList(first, size).clear(): that allocates, for every block Fabric's renderer draws.
			while (parts.size() > first) parts.removeLast();
		}
	}

	/**
	 * {@code FabricBlockModelPart.emitQuads}, with NeoForge's ambient occlusion (the part's, unless a quad turns it off)
	 * and the colours and normals NeoForge's quads carry, which upstream's {@code fromBakedQuad} doesn't know.
	 */
	public static void emitQuads(BlockModelPart part, QuadEmitter emitter, Predicate<@Nullable Direction> cullTest) {
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
				BakedColors colors = quad.bakedColors();
				if (colors != BakedColors.DEFAULT) {
					for (int v = 0; v < 4; v++) emitter.color(v, colors.color(v));
				}
				BakedNormals normals = quad.bakedNormals();
				if (normals != BakedNormals.UNSPECIFIED) {
					for (int v = 0; v < 4; v++) {
						int n = normals.normal(v);
						if (!BakedNormals.isUnspecified(n)) emitter.normal(v, BakedNormals.unpackX(n), BakedNormals.unpackY(n), BakedNormals.unpackZ(n));
					}
				}
				emitter.ambientOcclusion(quad.hasAmbientOcclusion() ? ambientOcclusion : TriState.FALSE);
				emitter.shadeMode(ShadeMode.VANILLA);
				emitter.emit();
			}
		}
	}
}
