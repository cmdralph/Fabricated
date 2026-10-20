package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.resources.model.WeightedVariants;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;

/**
 * Lets a vanilla weighted model say whether one of its variants is a Fabric model, once (1.21.11): then Fabric's
 * renderer draws the block (see {@link FabricBlockRendering}).
 */
@Mixin(WeightedVariants.class)
abstract class WeightedVariantsMixin implements FabricBlockRendering.Composite {
	@Shadow
	@Final
	private WeightedList<BlockStateModel> list;

	/** Null until first asked. Threads racing here compute the same answer. */
	@Unique
	private @Nullable Boolean fabricated$fabricPart;

	@Override
	public boolean fabricated$hasFabricPart() {
		Boolean known = fabricated$fabricPart;
		if (known == null) {
			known = false;
			for (Weighted<BlockStateModel> variant : list.unwrap()) {
				if (FabricBlockRendering.draws(variant.value())) {
					known = true;
					break;
				}
			}
			fabricated$fabricPart = known;
		}
		return known;
	}
}
