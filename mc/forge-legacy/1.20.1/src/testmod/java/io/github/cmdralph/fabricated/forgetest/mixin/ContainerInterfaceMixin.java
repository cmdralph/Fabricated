package io.github.cmdralph.fabricated.forgetest.mixin;

import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import io.github.cmdralph.fabricated.forgetest.ForgeInterfaceInjectorTest;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * An interface mixin with the injectors Fabricated applies where Forge 47's Mixin applies none in an interface mixin
 * (INTERFACE-1), each acting on {@link ForgeInterfaceInjectorTest}'s container alone. For {@code ForgeInterfaceInjectorTest}.
 */
@Mixin(Container.class)
public interface ContainerInterfaceMixin {
	/** Before each getItem call of countItem, with the count so far and the slot, as Origins' armour hook captures its locals. */
	@Inject(method = "countItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Container;getItem(I)Lnet/minecraft/world/item/ItemStack;"),
			locals = LocalCapture.CAPTURE_FAILHARD)
	private void fabricatedForgeTest$beforeGetItem(Item item, CallbackInfoReturnable<Integer> cir, int count, int slot) {
		if ((Object) this instanceof ForgeInterfaceInjectorTest.TestContainer) ForgeInterfaceInjectorTest.SEEN.add("slot " + slot + " after " + count);
	}

	/** countItem's count of each stack, tenfold, given the item counted. */
	@WrapOperation(method = "countItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getCount()I"))
	private int fabricatedForgeTest$tenfold(ItemStack stack, Operation<Integer> getCount, Item item) {
		int count = getCount.call(stack);
		return (Object) this instanceof ForgeInterfaceInjectorTest.TestContainer && stack.is(item) ? count * 10 : count;
	}

	/** hasAnyMatching's test of each stack, given the slot, as ServerCore's fluid hook takes its block state. */
	@Redirect(method = "hasAnyMatching", at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
	private boolean fabricatedForgeTest$test(Predicate<ItemStack> test, Object stack, @Local int slot) {
		if (!((Object) this instanceof ForgeInterfaceInjectorTest.TestContainer)) return test.test((ItemStack) stack);
		ForgeInterfaceInjectorTest.SEEN.add("test " + slot);
		return slot == 1;
	}

	/** getMaxStackSize's 64. */
	@ModifyConstant(method = "getMaxStackSize", constant = @Constant(intValue = 64))
	private int fabricatedForgeTest$maxStackSize(int max) {
		return (Object) this instanceof ForgeInterfaceInjectorTest.TestContainer ? 7 : max;
	}
}
