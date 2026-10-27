package net.fabricmc.fabric.api.transfer.v1.context;

import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;

public interface ContainerItemContext {
	static ContainerItemContext ofSingleSlot(SingleSlotStorage<ItemVariant> slot) {
		throw new AssertionError("stand-in");
	}

	default <A> A find(ItemApiLookup<A, ContainerItemContext> lookup) {
		throw new AssertionError("stand-in");
	}

	default ItemVariant getItemVariant() {
		throw new AssertionError("stand-in");
	}
}
