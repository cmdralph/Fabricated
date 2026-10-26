package net.fabricmc.fabric.api.loot.v1;

import net.minecraft.class_52;
import net.minecraft.class_55;

/** Upstream's override of {@code pool} keeps its name in Intermediary's names: its return type is upstream's own. */
public class FabricLootSupplierBuilder extends class_52.class_53 {
	protected FabricLootSupplierBuilder() {
	}

	public FabricLootSupplierBuilder pool(class_55.class_56 pool) {
		throw new AssertionError("stand-in");
	}
}
