package io.github.cmdralph.fabricated.forge.item.mixin;

import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.forge.item.ItemCompat;
import io.github.cmdralph.fabricated.forge.item.ItemHooks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeItem;

/**
 * Forge 61's stack-aware crafting remainder, which its furnaces, brewing stands and crafting ask where upstream's hooks
 * look for vanilla's item-level call, answered from Fabric's {@code FabricItem.getRecipeRemainder} ({@link ItemCompat}:
 * 1.21.11 names the two apart; from 26.1 they are one method).
 */
@Mixin(Item.class)
abstract class ItemRecipeRemainderMixin implements IForgeItem {
	@Override
	public ItemStack getCraftingRemainder(ItemStack stack) {
		return ItemHooks.ENABLED ? ItemCompat.recipeRemainder((Item) (Object) this, stack) : IForgeItem.super.getCraftingRemainder(stack);
	}
}
