package io.github.cmdralph.fabricated.forge.interaction.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import io.github.cmdralph.fabricated.forge.interaction.BlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fires Fabric's block break events ({@link BlockBreakEvents}) where upstream fires them, in Forge's
 * {@code destroyBlock}: {@code BEFORE} once the game's own checks let the player break the block (upstream: before
 * {@code playerWillDestroy}, which Forge moved into its {@code removeBlock}), returning false as upstream does when a
 * listener refuses; {@code AFTER} once either of the method's calls to Forge's {@code removeBlock} removed it (upstream:
 * at {@code Block.destroy}, which Forge calls there), before the drops. Both pass the state the method read first,
 * which is also the one Forge breaks the block in.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeBreakMixin {
	@Shadow
	protected ServerLevel level;

	@Shadow
	@Final
	protected ServerPlayer player;

	@Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;preventsBlockDrops()Z"), cancellable = true)
	private void fabricated$beforeBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir, @Local BlockEntity blockEntity,
			@Local(ordinal = 0) BlockState state) {
		if (!BlockBreakEvents.before(this.level, this.player, pos, state, blockEntity)) cir.setReturnValue(false);
	}

	@WrapOperation(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
	private boolean fabricated$afterBreak(ServerPlayerGameMode gameMode, BlockPos pos, boolean canHarvest, Operation<Boolean> removeBlock,
			@Local BlockEntity blockEntity, @Local(ordinal = 0) BlockState state) {
		boolean removed = removeBlock.call(gameMode, pos, canHarvest);
		if (removed) BlockBreakEvents.after(this.level, this.player, pos, state, blockEntity);
		return removed;
	}
}
