package net.fabricmc.fabric.impl.item;

import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.item.v1.CustomDamageHandler;
import net.fabricmc.fabric.api.item.v1.EquipmentSlotProvider;
import net.minecraft.world.item.Item;

/**
 * Fabric's per-item settings that {@code Item.Properties} carries until the item is built (equipment slot provider,
 * custom damage handler), as upstream. Fabricated's item mixin copies them onto the item when it is constructed.
 */
public final class FabricItemInternals {
	private static final Map<Item.Properties, ExtraData> EXTRA_DATA = new WeakHashMap<>();

	private FabricItemInternals() {
	}

	public static ExtraData computeExtraData(Item.Properties properties) {
		synchronized (EXTRA_DATA) {
			return EXTRA_DATA.computeIfAbsent(properties, p -> new ExtraData());
		}
	}

	public static void onBuild(Item.Properties properties, Item item) {
		ExtraData data;
		synchronized (EXTRA_DATA) {
			data = EXTRA_DATA.get(properties);
		}
		if (data != null) {
			((ItemExtensions) item).fabric_setEquipmentSlotProvider(data.equipmentSlotProvider);
			((ItemExtensions) item).fabric_setCustomDamageHandler(data.customDamageHandler);
		}
	}

	public static final class ExtraData {
		private @Nullable EquipmentSlotProvider equipmentSlotProvider;
		private @Nullable CustomDamageHandler customDamageHandler;

		public void equipmentSlot(EquipmentSlotProvider equipmentSlotProvider) {
			this.equipmentSlotProvider = equipmentSlotProvider;
		}

		public void customDamage(CustomDamageHandler handler) {
			this.customDamageHandler = handler;
		}
	}
}
