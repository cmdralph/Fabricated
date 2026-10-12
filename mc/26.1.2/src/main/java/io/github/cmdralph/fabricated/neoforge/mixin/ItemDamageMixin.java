package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.neoforge.item.ItemApiBridge;
import net.fabricmc.fabric.api.item.v1.CustomDamageHandler;
import net.fabricmc.fabric.impl.item.ItemExtensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IItemExtension;

/**
 * Fabric's custom damage handler, answered from NeoForge's {@code damageItem} hook (26.1.2 and 26.2, where it reports
 * the broken item). Only adds a method.
 */
@Mixin(Item.class)
abstract class ItemDamageMixin implements IItemExtension, ItemExtensions {
	@Override
	public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<Item> onBroken) {
		CustomDamageHandler handler = this.fabric_getCustomDamageHandler();
		if (handler == null) return IItemExtension.super.damageItem(stack, amount, entity, onBroken);
		return ItemApiBridge.customDamage(handler, stack, amount, entity, () -> onBroken.accept(stack.getItem()));
	}
}
