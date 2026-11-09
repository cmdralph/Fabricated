package io.github.cmdralph.fabricated.forge.item.mixin;

import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.forge.item.ItemHooks;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.common.extensions.IForgeItem;

/**
 * Forge 61 to 66's enchanting table check, which their enchantment helper asks with the enchantment's holder, answered
 * from Fabric's item API ({@link ItemHooks}); mc/forge-shared's {@code ItemForgeHooksMixin} answers Forge's other item
 * hooks. Only adds a method to {@code Item}; an item overriding it keeps its own.
 */
@Mixin(Item.class)
abstract class ItemEnchantingHookMixin implements IForgeItem {
	@Override
	public boolean canApplyAtEnchantingTable(ItemStack stack, Holder<Enchantment> enchantment) {
		return ItemHooks.ENABLED ? ItemHooks.canApplyAtEnchantingTable(stack, enchantment) : IForgeItem.super.canApplyAtEnchantingTable(stack, enchantment);
	}
}
