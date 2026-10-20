package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import net.minecraft.client.renderer.block.model.multipart.MultiPartModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets a vanilla multipart model say whether one of the parts its block state selects is a Fabric model, once (1.21.11):
 * then Fabric's renderer draws the whole block, the vanilla parts too (see {@link FabricBlockRendering}).
 */
@Mixin(MultiPartModel.class)
abstract class MultiPartModelMixin implements FabricBlockRendering.Composite {
	@Shadow
	@Final
	private MultiPartModel.SharedBakedState shared;
	@Shadow
	@Final
	private BlockState blockState;

	/** Null until first asked. Threads racing here compute the same answer. */
	@Unique
	private @Nullable Boolean fabricated$fabricPart;

	@Override
	public boolean fabricated$hasFabricPart() {
		Boolean known = fabricated$fabricPart;
		if (known == null) {
			known = FabricBlockRendering.anyDrawn(shared.selectModels(blockState));
			fabricated$fabricPart = known;
		}
		return known;
	}
}
