package net.fabricmc.fabric.impl.item;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.item.v1.CustomDamageHandler;
import net.fabricmc.fabric.api.item.v1.EquipmentSlotProvider;

/** Fabric's per-item settings, which Fabricated's item mixin adds to every item. */
public interface ItemExtensions {
	@Nullable EquipmentSlotProvider fabric_getEquipmentSlotProvider();

	void fabric_setEquipmentSlotProvider(@Nullable EquipmentSlotProvider equipmentSlotProvider);

	@Nullable CustomDamageHandler fabric_getCustomDamageHandler();

	void fabric_setCustomDamageHandler(@Nullable CustomDamageHandler handler);
}
