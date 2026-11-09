package io.github.cmdralph.fabricated.forge.rendering.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import io.github.cmdralph.fabricated.forge.rendering.FrapiFailures;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.util.random.WeightedEntry;

/**
 * Lets a vanilla weighted model (random variants) say whether one of its variants is a Fabric model (Forge 52): then
 * Fabric's renderer draws the block, whichever variant it picks, as upstream's own mixin on it does (see
 * {@link FabricBlockRendering}). A variant whose drawing threw makes Forge's renderer draw the block (FRAPI rule 8).
 */
@Mixin(WeightedBakedModel.class)
abstract class WeightedBakedModelMixin implements FabricBlockRendering.Composite {
	@Shadow
	@Final
	private List<WeightedEntry.Wrapper<BakedModel>> list;

	/** The state its variants give, for one generation of broken classes (see {@link FrapiFailures#cachedState}); 0 until asked. */
	@Unique
	private int fabricated$state;

	@Override
	public int fabricated$state() {
		int generation = FrapiFailures.generation();
		int state = FrapiFailures.cachedState(fabricated$state, generation);
		if (state < 0) {
			state = FrapiFailures.NONE;
			for (WeightedEntry.Wrapper<BakedModel> variant : list) state = FabricBlockRendering.combine(state, variant.data());
			fabricated$state = FrapiFailures.cache(state, generation);
		}
		return state;
	}

	@Override
	public List<BakedModel> fabricated$parts() {
		List<BakedModel> parts = new ArrayList<>(list.size());
		for (WeightedEntry.Wrapper<BakedModel> variant : list) parts.add(variant.data());
		return parts;
	}
}
