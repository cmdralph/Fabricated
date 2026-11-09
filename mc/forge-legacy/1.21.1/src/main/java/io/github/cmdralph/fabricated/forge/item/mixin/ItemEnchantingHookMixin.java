package io.github.cmdralph.fabricated.forge.item.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import io.github.cmdralph.fabricated.forge.item.ItemHooks;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.common.extensions.IForgeItem;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Forge 52's enchanting table check, which its {@code EnchantmentHelper} asks for each enchantment the table may offer
 * where upstream's hook looks for vanilla's {@code isPrimaryItem}, answered from Fabric's item API ({@link ItemHooks}).
 * Forge's hook gets the enchantment without its holder, which Fabric's API takes: it is the running server's (the table
 * offers enchantments there, from the server's registry), else the enchantment alone. Only adds a method to
 * {@code Item}; an item overriding it keeps its own.
 */
@Mixin(Item.class)
abstract class ItemEnchantingHookMixin implements IForgeItem {
	@Override
	public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
		return ItemHooks.ENABLED ? ItemHooks.canApplyAtEnchantingTable(stack, fabricated$holder(enchantment))
				: IForgeItem.super.canApplyAtEnchantingTable(stack, enchantment);
	}

	@Unique
	private static Holder<Enchantment> fabricated$holder(Enchantment enchantment) {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		return server == null ? Holder.direct(enchantment) : server.registryAccess().registryOrThrow(Registries.ENCHANTMENT).wrapAsHolder(enchantment);
	}
}
