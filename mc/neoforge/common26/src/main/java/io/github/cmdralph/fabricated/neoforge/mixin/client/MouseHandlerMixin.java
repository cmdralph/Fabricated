package io.github.cmdralph.fabricated.neoforge.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.neoforge.interaction.ClientHotbarScroll;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Inventory;

/** Fabric's {@code ClientHotbarScrollEvents}, as upstream: around the hotbar slot change of a mouse scroll. */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	@WrapOperation(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
	private void fabricated$scrollHotbar(Inventory inventory, int slot, Operation<Void> original) {
		ClientHotbarScroll.scroll(inventory, slot, () -> original.call(inventory, slot));
	}
}
