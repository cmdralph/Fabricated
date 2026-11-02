package io.github.cmdralph.fabricated.forge.rendering.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import io.github.cmdralph.fabricated.forge.rendering.FabricBlockRendering;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A multipart model's shared state (package-private in vanilla) answers {@link MultiPartModelMixin} with the models a
 * block state draws, as upstream's emitter selects them.
 */
@Mixin(targets = "net.minecraft.client.renderer.block.dispatch.multipart.MultiPartModel$SharedBakedState")
abstract class MultiPartSharedStateMixin implements FabricBlockRendering.ModelSelector {
	@Shadow
	public abstract List<BlockStateModel> selectModels(BlockState state);

	@Override
	public List<BlockStateModel> fabricated$selectModels(BlockState state) {
		return selectModels(state);
	}
}
