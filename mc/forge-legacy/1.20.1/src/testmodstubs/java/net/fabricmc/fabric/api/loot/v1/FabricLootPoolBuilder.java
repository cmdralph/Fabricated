package net.fabricmc.fabric.api.loot.v1;

import net.minecraft.class_55;
import net.minecraft.class_79;

/** Upstream's override of {@code with} keeps its name in Intermediary's names: its return type is upstream's own. */
public class FabricLootPoolBuilder extends class_55.class_56 {
	private FabricLootPoolBuilder() {
	}

	public static FabricLootPoolBuilder builder() {
		throw new AssertionError("stand-in");
	}

	public FabricLootPoolBuilder with(class_79.class_80<?> entry) {
		throw new AssertionError("stand-in");
	}
}
