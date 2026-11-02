package io.github.cmdralph.fabricated.forge.rendering.mixin;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import io.github.cmdralph.fabricated.forge.rendering.FrapiFailures;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.multipart.MultiPartModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A multipart model (one per block state) says whether a part its state selects is a Fabric model, or broken, and
 * draws its parts one by one through Fabricated's guard once one is (FRAPI rule 8): upstream's emitter would call the
 * broken part again for every block. The same seed for each part, as upstream's emitter.
 *
 * <p>Its shared state is package-private in vanilla (upstream's access widener opens it to Fabric mods, not to
 * Fabricated's build): it is kept as it is given to the constructor, and asked through {@link MultiPartSharedStateMixin}.
 */
@Mixin(MultiPartModel.class)
abstract class MultiPartModelMixin implements FabricBlockRendering.Composite {
	@Shadow
	@Final
	private BlockState blockState;

	/** The shared state, which selects the models this one's state draws; null only if its mixin didn't apply. */
	@Unique
	private FabricBlockRendering.@Nullable ModelSelector fabricated$shared;
	/** The state its parts give, for one generation of broken classes (see {@link FrapiFailures#cachedState}); 0 until asked. */
	@Unique
	private int fabricated$state;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void fabricated$keepShared(@Coerce Object shared, BlockState state, CallbackInfo ci) {
		if (shared instanceof FabricBlockRendering.ModelSelector selector) fabricated$shared = selector;
	}

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
		FabricBlockRendering.ModelSelector shared = fabricated$shared;
		return shared == null ? List.of() : shared.fabricated$selectModels(blockState);
	}

	@Override
	public void fabricated$emitEach(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest, String where) {
		long seed = random.nextLong();
		for (BlockStateModel model : fabricated$parts()) {
			random.setSeed(seed);
			FabricBlockRendering.emit(model, emitter, level, pos, state, random, cullTest, where);
		}
	}
}
