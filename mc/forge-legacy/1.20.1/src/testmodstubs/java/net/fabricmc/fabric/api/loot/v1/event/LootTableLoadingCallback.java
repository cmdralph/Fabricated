package net.fabricmc.fabric.api.loot.v1.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.loot.v1.FabricLootSupplierBuilder;
import net.minecraft.class_2960;
import net.minecraft.class_3300;
import net.minecraft.class_52;
import net.minecraft.class_60;

public interface LootTableLoadingCallback {
	Event<LootTableLoadingCallback> EVENT = null;

	void onLootTableLoading(class_3300 resourceManager, class_60 lootManager, class_2960 id, FabricLootSupplierBuilder table, LootTableSetter setter);

	interface LootTableSetter {
		void set(class_52 supplier);
	}
}
