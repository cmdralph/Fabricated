package net.fabricmc.fabric.api.transfer.v1.context;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod and the forge test mod
 * call, in Mojang's names.
 */
public interface ContainerItemContext {
	static ContainerItemContext ofSingleSlot(SingleSlotStorage<ItemVariant> slot) {
		throw new AssertionError("stand-in");
	}

	@Nullable
	default <A> A find(ItemApiLookup<A, ContainerItemContext> lookup) {
		throw new AssertionError("stand-in");
	}

	default ItemVariant getItemVariant() {
		throw new AssertionError("stand-in");
	}

	default long getAmount() {
		throw new AssertionError("stand-in");
	}

	default long extract(ItemVariant itemVariant, long maxAmount, TransactionContext transaction) {
		throw new AssertionError("stand-in");
	}

	default long exchange(ItemVariant newVariant, long maxAmount, TransactionContext transaction) {
		throw new AssertionError("stand-in");
	}
}
