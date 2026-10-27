package net.fabricmc.fabric.api.transfer.v1.storage;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

public interface Storage<T> {
	long insert(T resource, long maxAmount, TransactionContext transaction);

	long extract(T resource, long maxAmount, TransactionContext transaction);
}
