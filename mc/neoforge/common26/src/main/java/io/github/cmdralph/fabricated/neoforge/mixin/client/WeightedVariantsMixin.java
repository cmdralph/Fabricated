package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import io.github.cmdralph.fabricated.neoforge.rendering.FrapiFailures;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.WeightedVariants;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;

/**
 * A weighted model says whether one of its variants is a Fabric model: then Fabric's renderer draws the block, whose
 * vanilla variants it draws as NeoForge would (see {@link FabricBlockRendering}). The variant is picked per position,
 * so any Fabric variant decides for all of them, and a variant whose drawing threw makes NeoForge's renderer draw them
 * all (FRAPI rule 8).
 */
@Mixin(WeightedVariants.class)
abstract class WeightedVariantsMixin implements FabricBlockRendering.Composite {
	@Shadow
	@Final
	private WeightedList<BlockStateModel> list;

	/** The state its variants give, for one generation of broken classes (see {@link FrapiFailures#cachedState}); 0 until asked. */
	@Unique
	private int fabricated$state;

	@Override
	public int fabricated$state() {
		int generation = FrapiFailures.generation();
		int state = FrapiFailures.cachedState(fabricated$state, generation);
		if (state < 0) {
			state = FabricBlockRendering.state(fabricated$parts());
			fabricated$state = FrapiFailures.cache(state, generation);
		}
		return state;
	}

	@Override
	public List<BlockStateModel> fabricated$parts() {
		List<BlockStateModel> parts = new ArrayList<>();
		for (Weighted<BlockStateModel> variant : list.unwrap()) parts.add(variant.value());
		return parts;
	}
}
