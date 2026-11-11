package io.github.cmdralph.fabricated.neoforge.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.neoforge.interaction.InteractionBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fires Fabric's {@code PlayerBlockBreakEvents.AFTER} once a player's break removed the block. It observes NeoForge's
 * {@code removeBlock}, which both of {@code destroyBlock}'s paths go through (Minecraft 1.21.1: without the tool), or
 * vanilla's calls put back there (SEAM-3); nothing is changed.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
	@Shadow
	protected ServerLevel level;

	@Shadow
	@Final
	protected ServerPlayer player;

	@Unique
	private @Nullable BlockEntity fabricated$removedBlockEntity;

	@Unique
	private @Nullable BlockState fabricated$removedState;

	@Inject(method = "removeBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Z", at = @At("HEAD"))
	private void fabricated$beforeRemoveBlock(BlockPos pos, BlockState state, boolean canHarvest, CallbackInfoReturnable<Boolean> cir) {
		this.fabricated$removedBlockEntity = this.level.getBlockEntity(pos);
	}

	@Inject(method = "removeBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Z", at = @At("RETURN"))
	private void fabricated$afterRemoveBlock(BlockPos pos, BlockState state, boolean canHarvest, CallbackInfoReturnable<Boolean> cir) {
		BlockEntity blockEntity = this.fabricated$removedBlockEntity;
		this.fabricated$removedBlockEntity = null;
		if (cir.getReturnValueZ()) InteractionBridge.afterBreak(this.level, this.player, pos, state, blockEntity);
	}

	/**
	 * Where Fabricated puts vanilla's calls back in {@code destroyBlock} for a Fabric mod's hook of them (SEAM-3), a block
	 * keeping NeoForge's {@code onDestroyedByPlayer} is broken there, without {@code removeBlock}: its state and block entity
	 * are read before vanilla's {@code removeBlock}, and the event fired at vanilla's {@code Block.destroy}, which follows
	 * where the block was removed, as upstream fires it. Neither call is there otherwise.
	 */
	@Inject(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"),
			require = 0)
	private void fabricated$beforeVanillaRemoval(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		this.fabricated$removedBlockEntity = this.level.getBlockEntity(pos);
		this.fabricated$removedState = this.level.getBlockState(pos);
	}

	@Inject(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;destroy(Lnet/minecraft/world/level/LevelAccessor;"
			+ "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"), require = 0)
	private void fabricated$afterVanillaRemoval(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		BlockState state = this.fabricated$removedState;
		BlockEntity blockEntity = this.fabricated$removedBlockEntity;
		this.fabricated$removedState = null;
		this.fabricated$removedBlockEntity = null;
		if (state != null) InteractionBridge.afterBreak(this.level, this.player, pos, state, blockEntity);
	}
}
