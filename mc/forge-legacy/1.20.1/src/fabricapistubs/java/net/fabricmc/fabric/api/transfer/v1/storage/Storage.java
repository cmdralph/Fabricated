package net.fabricmc.fabric.api.transfer.v1.storage;

import java.util.Iterator;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/**
 * Compile-time stand-in: the members of Fabric API's 1.20.1 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public interface Storage<T> extends Iterable<StorageView<T>> {
	default boolean supportsInsertion() {
		throw new AssertionError("stand-in");
	}

	long insert(T resource, long maxAmount, TransactionContext transaction);

	default boolean supportsExtraction() {
		throw new AssertionError("stand-in");
	}

	long extract(T resource, long maxAmount, TransactionContext transaction);

	@Override
	Iterator<StorageView<T>> iterator();

	default Iterable<StorageView<T>> nonEmptyViews() {
		throw new AssertionError("stand-in");
	}
}
