package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.fabricmc.fabric.api.event.player.BlockEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Fabric's {@code BlockEvents.USE_WITHOUT_ITEM}, as upstream: NeoForge has no event for this method (its
 * {@code UseItemOnBlockEvent} covers {@code useItemOn} only). A listener's result replaces the block's; without one,
 * nothing changes.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseMixin {
	@Shadow
	protected abstract BlockState asState();

	@Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
	private void fabricated$useWithoutItem(Level level, Player player, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
		InteractionResult result = BlockEvents.USE_WITHOUT_ITEM.invoker().useWithoutItem(this.asState(), level, hitResult.getBlockPos(), player, hitResult);
		if (result != null) cir.setReturnValue(result);
	}
}
