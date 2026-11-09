package io.github.cmdralph.fabricated.forge.item.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.forge.item.ItemHooks;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeItem;

/**
 * Answers Forge's item hooks from Fabric's item API ({@link ItemHooks}): the equipment slot and the crafting remainder.
 * Only adds methods to {@code Item}; an item overriding them keeps its own.
 */
@Mixin(Item.class)
abstract class ItemForgeHooksMixin implements IForgeItem {
	@Override
	public @Nullable EquipmentSlot getEquipmentSlot(ItemStack stack) {
		return ItemHooks.equipmentSlot((Item) (Object) this, stack);
	}

	@Override
	public ItemStack getCraftingRemainingItem(ItemStack stack) {
		return ItemHooks.ENABLED ? ItemHooks.recipeRemainder((Item) (Object) this, stack) : IForgeItem.super.getCraftingRemainingItem(stack);
	}

	@Override
	public boolean hasCraftingRemainingItem(ItemStack stack) {
		return ItemHooks.ENABLED ? !ItemHooks.recipeRemainder((Item) (Object) this, stack).isEmpty() : IForgeItem.super.hasCraftingRemainingItem(stack);
	}
}
