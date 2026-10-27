package net.fabricmc.fabric.api.transfer.v1.storage.base;

import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/** Its type bound is upstream's: a subclass's bridge methods implement the abstract methods by their erasure. */
public abstract class SingleVariantStorage<T extends TransferVariant<?>> implements Storage<T> {
	public T variant;
	public long amount;

	protected abstract T getBlankVariant();

	protected abstract long getCapacity(T variant);

	@Override
	public long insert(T resource, long maxAmount, TransactionContext transaction) {
		throw new AssertionError("stand-in");
	}

	@Override
	public long extract(T resource, long maxAmount, TransactionContext transaction) {
		throw new AssertionError("stand-in");
	}
}
