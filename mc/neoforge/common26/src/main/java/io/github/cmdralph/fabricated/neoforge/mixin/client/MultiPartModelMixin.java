package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import io.github.cmdralph.fabricated.neoforge.rendering.FrapiFailures;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.multipart.MultiPartModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A multipart model (one per block state) says whether a part its state selects is a Fabric model: then Fabric's
 * renderer draws the whole block, whose vanilla parts it draws as NeoForge would (see {@link FabricBlockRendering}).
 * NeoForge's renderer only asks the parts for theirs, which a Fabric model doesn't have. A part whose drawing threw makes
 * NeoForge's renderer draw the whole block (FRAPI rule 8): Fabric's would ask that part again.
 */
@Mixin(MultiPartModel.class)
abstract class MultiPartModelMixin implements FabricBlockRendering.Composite {
	@Shadow
	@Final
	private MultiPartModel.SharedBakedState shared;
	@Shadow
	@Final
	private BlockState blockState;

	/** The state its parts give, for one generation of broken classes (see {@link FrapiFailures#cachedState}); 0 until asked. */
	@Unique
	private int fabricated$state;

	@Override
	public int fabricated$state() {
		int generation = FrapiFailures.generation();
		int state = FrapiFailures.cachedState(fabricated$state, generation);
		if (state < 0) {
			state = FabricBlockRendering.state(shared.selectModels(blockState));
			fabricated$state = FrapiFailures.cache(state, generation);
		}
		return state;
	}

	@Override
	public List<BlockStateModel> fabricated$parts() {
		return shared.selectModels(blockState);
	}
}
