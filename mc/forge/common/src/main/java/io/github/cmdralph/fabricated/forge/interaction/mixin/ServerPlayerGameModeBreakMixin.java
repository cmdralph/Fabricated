package io.github.cmdralph.fabricated.forge.interaction.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

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
 * at {@code Block.destroy}, which Forge calls there), before the drops. Both pass the block's state and block entity
 * as the world holds them before the removal: the state {@code removeBlock} reads and breaks the block in. Where
 * Fabricated puts vanilla's calls back in {@code destroyBlock} for a Fabric mod's hook of them (SEAM-3), a block keeping
 * Forge's {@code onDestroyedByPlayer} is broken there without {@code removeBlock}: its state and block entity are read
 * before vanilla's {@code removeBlock}, and {@code AFTER} fired at vanilla's {@code Block.destroy}, which follows where the
 * block was removed, as upstream fires it. Neither call is there otherwise.
 *
 * <p>No MixinExtras sugar ({@code @Local}): without Fabric API installed, Forge's MixinExtras can't load the Mixin
 * class it needs, as {@code ReloadableServerRegistriesMixin} notes.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeBreakMixin {
	@Shadow
	protected ServerLevel level;

	@Shadow
	@Final
	protected ServerPlayer player;

	@Unique
	private BlockState fabricated$removedState;

	@Unique
	private BlockEntity fabricated$removedBlockEntity;

	@Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;preventsBlockDrops()Z"), cancellable = true)
	private void fabricated$beforeBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (!BlockBreakEvents.before(this.level, this.player, pos, this.level.getBlockState(pos), this.level.getBlockEntity(pos))) {
			cir.setReturnValue(false);
		}
	}

	@WrapOperation(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
	private boolean fabricated$afterBreak(ServerPlayerGameMode gameMode, BlockPos pos, boolean canHarvest, Operation<Boolean> removeBlock) {
		BlockState state = this.level.getBlockState(pos);
		BlockEntity blockEntity = this.level.getBlockEntity(pos);
		boolean removed = removeBlock.call(gameMode, pos, canHarvest);
		if (removed) BlockBreakEvents.after(this.level, this.player, pos, state, blockEntity);
		return removed;
	}

	@Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"), require = 0)
	private void fabricated$beforeVanillaRemoval(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		this.fabricated$removedState = this.level.getBlockState(pos);
		this.fabricated$removedBlockEntity = this.level.getBlockEntity(pos);
	}

	@Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/Block;destroy(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"),
			require = 0)
	private void fabricated$afterVanillaRemoval(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		BlockState state = this.fabricated$removedState;
		BlockEntity blockEntity = this.fabricated$removedBlockEntity;
		this.fabricated$removedState = null;
		this.fabricated$removedBlockEntity = null;
		if (state != null) BlockBreakEvents.after(this.level, this.player, pos, state, blockEntity);
	}
}
