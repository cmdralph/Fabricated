package io.github.cmdralph.fabricated.forge.item.mixin;

import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.forge.item.ItemCompat;
import io.github.cmdralph.fabricated.forge.item.ItemHooks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeItem;

/**
 * Forge 52's stack-aware crafting remainder, which its furnaces, brewing stands and crafting ask where upstream's hooks
 * look for vanilla's item-level calls, answered from Fabric's {@code FabricItem.getRecipeRemainder} ({@link ItemCompat}),
 * as on Forge 47. Only adds methods to {@code Item}; an item overriding them keeps its own.
 */
@Mixin(Item.class)
abstract class ItemRecipeRemainderMixin implements IForgeItem {
	@Override
	public ItemStack getCraftingRemainingItem(ItemStack stack) {
		return ItemHooks.ENABLED ? ItemCompat.recipeRemainder((Item) (Object) this, stack) : IForgeItem.super.getCraftingRemainingItem(stack);
	}

	@Override
	public boolean hasCraftingRemainingItem(ItemStack stack) {
		return ItemHooks.ENABLED ? !ItemCompat.recipeRemainder((Item) (Object) this, stack).isEmpty() : IForgeItem.super.hasCraftingRemainingItem(stack);
	}
}
