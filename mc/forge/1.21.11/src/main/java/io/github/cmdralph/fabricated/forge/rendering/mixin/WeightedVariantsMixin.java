package io.github.cmdralph.fabricated.forge.rendering.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import io.github.cmdralph.fabricated.forge.rendering.FrapiFailures;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.resources.model.WeightedVariants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A weighted model says whether one of its variants is a Fabric model, or broken, and draws the variant picked for a
 * position through Fabricated's guard once one is (FRAPI rule 8), picked as upstream's emitter picks it.
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

	@Override
	public void fabricated$emitEach(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest, String where) {
		FabricBlockRendering.emit(list.getRandomOrThrow(random), emitter, level, pos, state, random, cullTest, where);
	}
}
