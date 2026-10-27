package net.fabricmc.fabric.api.transfer.v1.storage;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public interface StorageView<T> {
	long extract(T resource, long maxAmount, TransactionContext transaction);

	boolean isResourceBlank();

	T getResource();

	long getAmount();

	long getCapacity();
}
