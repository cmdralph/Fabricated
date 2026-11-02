package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import io.github.cmdralph.fabricated.neoforge.rendering.FrapiFailures;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.resources.model.WeightedVariants;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;

/**
 * Lets a vanilla weighted model say whether one of its variants is a Fabric model (1.21.11): then Fabric's renderer
 * draws the block (see {@link FabricBlockRendering}), unless one threw: then NeoForge's draws them all (FRAPI rule 8).
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
