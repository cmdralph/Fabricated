package io.github.cmdralph.fabricated.forge.interaction.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.interaction.InteractionEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.common.ForgeHooks;

/**
 * Fabric's {@code ItemEvents.USE_ON} where Forge's server uses an item on a block ({@link InteractionEvents}):
 * {@code ItemStack.useOn} hands the server's use to {@code ForgeHooks.onPlaceItemIntoWorld}, which calls the item's
 * {@code useOn} after its adventure mode check and inside its block snapshot capture, where vanilla's
 * {@code ItemStack.useOn} calls it and upstream's hook takes the call.
 */
@Mixin(ForgeHooks.class)
abstract class ForgeHooksUseOnMixin {
	@WrapOperation(method = "onPlaceItemIntoWorld",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
	private static InteractionResult fabricated$useOnEvent(Item item, UseOnContext context, Operation<InteractionResult> useOn) {
		InteractionResult result = InteractionEvents.useOn(context);
		return result != null ? result : useOn.call(item, context);
	}
}
