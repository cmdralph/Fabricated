package net.fabricmc.fabric.api.transfer.v1.storage.base;

import java.util.Iterator;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that the forge test mod calls, in Mojang's names. Its
 * type bound is upstream's: a subclass's bridge methods implement the abstract methods by their erasure.
 */
public abstract class SingleVariantItemStorage<T extends TransferVariant<?>> implements Storage<T> {
	public SingleVariantItemStorage(ContainerItemContext context) {
		throw new AssertionError("stand-in");
	}

	protected abstract T getBlankResource();

	protected abstract T getResource(ItemVariant currentVariant);

	protected abstract long getAmount(ItemVariant currentVariant);

	protected abstract long getCapacity(T variant);

	protected abstract ItemVariant getUpdatedVariant(ItemVariant currentVariant, T newResource, long newAmount);

	protected boolean canInsert(T resource) {
		throw new AssertionError("stand-in");
	}

	@Override
	public long insert(T resource, long maxAmount, TransactionContext transaction) {
		throw new AssertionError("stand-in");
	}

	@Override
	public long extract(T resource, long maxAmount, TransactionContext transaction) {
		throw new AssertionError("stand-in");
	}

	@Override
	public Iterator<StorageView<T>> iterator() {
		throw new AssertionError("stand-in");
	}
}
