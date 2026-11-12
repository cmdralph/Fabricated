package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.testmod.VanillaItemCheckTest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.TripWireBlock;

/**
 * BCLib's hook of vanilla's check for shears in the tripwire's {@code playerWillDestroy}, which NeoForge and Forge make
 * through the stack's {@code canPerformAction} and Fabricated puts back for the stacks whose item keeps its default
 * (SEAM-6, {@link VanillaItemCheckTest}): a {@code @WrapOperation} of the {@code is} call, which lets another item count
 * as shears. From 26.1 on, whose {@code ItemStack.is(Item)} is {@code TypedInstance.is(T)}, called as {@code is(Object)}.
 */
@Mixin(TripWireBlock.class)
public abstract class ShearsCheckMixin {
	@WrapOperation(method = "playerWillDestroy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
	private boolean fabricatedTest$shears(ItemStack stack, Object item, Operation<Boolean> original) {
		boolean vanilla = original.call(stack, item);
		return item == Items.SHEARS ? VanillaItemCheckTest.shears(stack, vanilla) : vanilla;
	}
}
