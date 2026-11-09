package io.github.cmdralph.fabricated.forge.item.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.forge.item.ItemHooks;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeItem;

/**
 * Answers Forge's item hooks from Fabric's item API ({@link ItemHooks}): the equipment slot and the creator mod. Each
 * version's {@code ItemEnchantingHookMixin} answers the enchanting table's check, whose hook Forge 52 gives the
 * enchantment and Forge 61 to 66 its holder. Only adds methods to {@code Item}; an item overriding them keeps its own.
 */
@Mixin(Item.class)
abstract class ItemForgeHooksMixin implements IForgeItem {
	@Override
	public @Nullable EquipmentSlot getEquipmentSlot(ItemStack stack) {
		return ItemHooks.equipmentSlot((Item) (Object) this, stack);
	}

	@Override
	public @Nullable String getCreatorModId(ItemStack stack) {
		String namespace = ItemHooks.creatorNamespace((Item) (Object) this, stack);
		return namespace != null ? namespace : IForgeItem.super.getCreatorModId(stack);
	}
}
