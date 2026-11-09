package io.github.cmdralph.fabricated.forge.item.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.item.ItemHooks;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Upstream's stack hooks on the calls Forge's {@code ItemStack} makes in place of vanilla's ({@link ItemHooks}): whether
 * the stack is the tool for a block, and the item's attribute modifiers with Fabric's callback over them. Forge's own
 * methods, whose names are not remapped.
 */
@Mixin(ItemStack.class)
abstract class ItemStackForgeHooksMixin {
	@WrapOperation(method = "isCorrectToolForDrops", at = @At(value = "INVOKE", remap = false,
			target = "Lnet/minecraft/world/item/Item;isCorrectToolForDrops(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean fabricated$fabricSuitableFor(Item item, ItemStack stack, BlockState state, Operation<Boolean> forge) {
		return ItemHooks.overridesSuitableFor(item) ? ItemHooks.isSuitableFor(item, stack, state) : forge.call(item, stack, state);
	}

	@WrapOperation(method = "getAttributeModifiers", at = @At(value = "INVOKE", remap = false,
			target = "Lnet/minecraft/world/item/Item;getAttributeModifiers(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)Lcom/google/common/collect/Multimap;"))
	private Multimap<Attribute, AttributeModifier> fabricated$fabricAttributeModifiers(Item item, EquipmentSlot slot, ItemStack stack,
			Operation<Multimap<Attribute, AttributeModifier>> forge) {
		if (!ItemHooks.ENABLED) return forge.call(item, slot, stack);
		Multimap<Attribute, AttributeModifier> modifiers = ItemHooks.overridesAttributeModifiers(item)
				? ItemHooks.attributeModifiers(item, stack, slot) : forge.call(item, slot, stack);
		return ItemHooks.modifyAttributeModifiers(stack, slot, modifiers);
	}
}
