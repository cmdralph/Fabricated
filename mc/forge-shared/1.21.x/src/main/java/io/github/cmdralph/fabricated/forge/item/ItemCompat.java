package io.github.cmdralph.fabricated.forge.item;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.item.v1.EquipmentSlotProvider;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The parts of {@link ItemHooks} 1.21.1's and 1.21.11's Fabric item API names its own way, and its stack-aware recipe
 * remainder, which Forge 52 asks as {@code getCraftingRemainingItem} and Forge 61 as {@code getCraftingRemainder}. Called
 * only with Fabric API's item module.
 */
public final class ItemCompat {
	private ItemCompat() {
	}

	static @Nullable EquipmentSlot equipmentSlot(EquipmentSlotProvider provider, ItemStack stack) {
		return provider.getPreferredEquipmentSlot(null, stack);
	}

	/** Fabric's {@code getRecipeRemainder}, whose default is vanilla's remainder, as Forge's is. */
	public static ItemStack recipeRemainder(Item item, ItemStack stack) {
		return ((FabricItem) item).getRecipeRemainder(stack);
	}
}
