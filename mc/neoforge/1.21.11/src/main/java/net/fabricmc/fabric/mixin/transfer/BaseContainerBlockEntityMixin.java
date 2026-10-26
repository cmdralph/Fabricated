package net.fabricmc.fabric.mixin.transfer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.fabric.impl.transfer.item.SpecialLogicInventory;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Fabricated's version of upstream's mixin: defers {@code setChanged} until the transaction's final commit while a
 * Fabric container wrapper sets a slot. Upstream redirects the {@code setChanged} call in {@code setItem}, which NeoForge
 * moved into its own {@code setItem(slot, stack, insideTransaction)}; that overload with {@code true} does the same.
 */
@Mixin(BaseContainerBlockEntity.class)
public class BaseContainerBlockEntityMixin implements SpecialLogicInventory {
	@Unique
	private boolean fabric_suppressSpecialLogic = false;

	@Inject(method = "setItem(ILnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
	private void fabricated$deferSetChanged(int slot, ItemStack stack, CallbackInfo ci) {
		if (this.fabric_suppressSpecialLogic) {
			((BaseContainerBlockEntity) (Object) this).setItem(slot, stack, true);
			ci.cancel();
		}
	}

	@Override
	public void fabric_setSuppress(boolean suppress) {
		this.fabric_suppressSpecialLogic = suppress;
	}

	@Override
	public void fabric_onFinalCommit(int slot, ItemStack oldStack, ItemStack newStack) {
	}
}
