package io.github.cmdralph.fabricated.forge.rendering.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import io.github.cmdralph.fabricated.forge.rendering.FrapiFailures;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets a vanilla multipart model say whether one of its parts is a Fabric model (Forge 47 and 52): then Fabric's
 * renderer draws the whole block, the vanilla parts too, as upstream's own mixin on it does (see
 * {@link FabricBlockRendering}). A part whose drawing threw makes Forge's renderer draw the whole block (FRAPI rule 8):
 * Fabric's would ask that part again.
 */
@Mixin(MultiPartBakedModel.class)
abstract class MultiPartBakedModelMixin implements FabricBlockRendering.Composite {
	@Shadow
	@Final
	private List<Pair<Predicate<BlockState>, BakedModel>> selectors;

	/** The state its parts give, for one generation of broken classes (see {@link FrapiFailures#cachedState}); 0 until asked. */
	@Unique
	private int fabricated$state;

	@Override
	public int fabricated$state() {
		int generation = FrapiFailures.generation();
		int state = FrapiFailures.cachedState(fabricated$state, generation);
		if (state < 0) {
			state = FrapiFailures.NONE;
			for (Pair<Predicate<BlockState>, BakedModel> selector : selectors) state = FabricBlockRendering.combine(state, selector.getRight());
			fabricated$state = FrapiFailures.cache(state, generation);
		}
		return state;
	}

	@Override
	public List<BakedModel> fabricated$parts() {
		List<BakedModel> parts = new ArrayList<>(selectors.size());
		for (Pair<Predicate<BlockState>, BakedModel> selector : selectors) parts.add(selector.getRight());
		return parts;
	}
}
