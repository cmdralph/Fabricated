package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.fabricmc.fabric.api.event.player.PlayerPickItemEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric's {@code PlayerPickItemEvents}, as upstream (NeoForge has no pick-item event): a listener's stack is given to
 * the player instead of the block's or entity's own, with vanilla's data copying skipped. The block hook wraps
 * NeoForge's player-aware {@code getCloneItemStack}, which NeoForge's patch calls there instead of vanilla's.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class PickItemMixin {
	@Shadow
	public ServerPlayer player;

	@Shadow
	private void tryPickItem(ItemStack stack) {
		throw new AssertionError();
	}

	@WrapOperation(method = "handlePickItemFromBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getCloneItemStack(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/LevelReader;ZLnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack fabricated$pickFromBlock(BlockState state, BlockPos pos, LevelReader level, boolean includeData, Player player,
			Operation<ItemStack> original, @Local(argsOnly = true) ServerboundPickItemFromBlockPacket packet) {
		ItemStack stack = PlayerPickItemEvents.BLOCK.invoker().onPickItemFromBlock(this.player, pos, state, packet.includeData());
		if (stack == null) return original.call(state, pos, level, includeData, player);
		if (!stack.isEmpty()) this.tryPickItem(stack);
		return ItemStack.EMPTY;
	}

	@WrapOperation(method = "handlePickItemFromEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getPickResult()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack fabricated$pickFromEntity(Entity entity, Operation<ItemStack> original, @Local(argsOnly = true) ServerboundPickItemFromEntityPacket packet) {
		ItemStack stack = PlayerPickItemEvents.ENTITY.invoker().onPickItemFromEntity(this.player, entity, packet.includeData());
		if (stack == null) return original.call(entity);
		if (!stack.isEmpty()) this.tryPickItem(stack);
		return ItemStack.EMPTY;
	}
}
