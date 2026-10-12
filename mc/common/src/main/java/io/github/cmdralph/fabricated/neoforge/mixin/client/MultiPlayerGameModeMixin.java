package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.neoforge.interaction.ClientInteractionBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fires Fabric's {@code ClientPlayerBlockBreakEvents.AFTER} when the client's {@code destroyBlock} removed the block,
 * with the state from before. Observes only.
 */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Unique
	private @Nullable BlockState fabricated$destroyedState;

	@Inject(method = "destroyBlock", at = @At("HEAD"))
	private void fabricated$beforeDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		this.fabricated$destroyedState = this.minecraft.level == null ? null : this.minecraft.level.getBlockState(pos);
	}

	@Inject(method = "destroyBlock", at = @At("RETURN"))
	private void fabricated$afterDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		BlockState state = this.fabricated$destroyedState;
		this.fabricated$destroyedState = null;
		if (cir.getReturnValueZ() && state != null) ClientInteractionBridge.afterBreak(this.minecraft.level, this.minecraft.player, pos, state);
	}
}
