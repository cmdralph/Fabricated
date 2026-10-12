package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Fabric's {@code ItemEvents.USE}, as upstream: a listener's result stands in for {@code Item.use} inside
 * {@code ItemStack.use}, which NeoForge doesn't patch (its {@code RightClickItem} comes earlier, from the game mode only).
 */
@Mixin(ItemStack.class)
abstract class ItemStackUseMixin {
	@WrapOperation(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
	private InteractionResult fabricated$use(Item item, Level level, Player player, InteractionHand hand, Operation<InteractionResult> original) {
		InteractionResult result = ItemEvents.USE.invoker().use(level, player, hand);
		return result != null ? result : original.call(item, level, player, hand);
	}
}
