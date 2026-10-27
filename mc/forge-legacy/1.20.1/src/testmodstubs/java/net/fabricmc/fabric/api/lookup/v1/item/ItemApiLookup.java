package net.fabricmc.fabric.api.lookup.v1.item;

import net.minecraft.class_1799;
import net.minecraft.class_1935;

public interface ItemApiLookup<A, C> {
	void registerForItems(ItemApiProvider<A, C> provider, class_1935... items);

	@FunctionalInterface
	interface ItemApiProvider<A, C> {
		A find(class_1799 itemStack, C context);
	}
}
