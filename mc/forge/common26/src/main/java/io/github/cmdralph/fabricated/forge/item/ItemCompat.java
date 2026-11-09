package io.github.cmdralph.fabricated.forge.item;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.item.v1.EquipmentSlotProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** The parts of {@link ItemHooks} 26.x's Fabric item API names its own way. Called only with Fabric API's item module. */
final class ItemCompat {
	private ItemCompat() {
	}

	static @Nullable EquipmentSlot equipmentSlot(EquipmentSlotProvider provider, ItemStack stack) {
		return provider.getEquipmentSlotForItem(null, stack);
	}
}
