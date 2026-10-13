package io.github.cmdralph.fabricated.neoforge.transfer;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A Fabric storage seen as a NeoForge resource handler. Slotted storages map slot for slot; other storages expose
 * their views as indices, with insertion going to the storage as a whole through index 0.
 */
final class FabricStorageHandler<F extends TransferVariant<?>, N extends Resource> implements ResourceHandler<N> {
	final Storage<F> storage;
	private final ResourceKind<F, N> kind;

	FabricStorageHandler(Storage<F> storage, ResourceKind<F, N> kind) {
		this.storage = storage;
		this.kind = kind;
	}

	@FunctionalInterface
	private interface Move<F> {
		long apply(F variant, long maxAmount, Transaction transaction);
	}

	@Override
	public int size() {
		if (storage instanceof SlottedStorage<F> slotted) return slotted.getSlotCount();
		int size = 0;
		for (StorageView<F> ignored : storage) size++;
		return Math.max(size, 1);
	}

	private @Nullable StorageView<F> view(int index) {
		if (storage instanceof SlottedStorage<F> slotted) return index >= 0 && index < slotted.getSlotCount() ? slotted.getSlot(index) : null;
		int i = 0;
		for (StorageView<F> view : storage) {
			if (i++ == index) return view;
		}
		return null;
	}

	@Override
	public N getResource(int index) {
		StorageView<F> view = view(index);
		return view == null || view.isResourceBlank() ? kind.neoEmpty() : kind.neo(view.getResource());
	}

	@Override
	public long getAmountAsLong(int index) {
		StorageView<F> view = view(index);
		return view == null ? 0 : view.getAmount() / kind.unit();
	}

	@Override
	public long getCapacityAsLong(int index, N resource) {
		StorageView<F> view = view(index);
		return view == null ? 0 : view.getCapacity() / kind.unit();
	}

	@Override
	public boolean isValid(int index, N resource) {
		return true;
	}

	@Override
	public int insert(int index, N resource, int amount, TransactionContext transaction) {
		Storage<F> target = storage instanceof SlottedStorage<F> slotted
				? (index >= 0 && index < slotted.getSlotCount() ? slotted.getSlot(index) : null)
				: (index == 0 ? storage : null);
		return target == null ? 0 : move(target::insert, resource, amount, transaction);
	}

	@Override
	public int extract(int index, N resource, int amount, TransactionContext transaction) {
		StorageView<F> view = view(index);
		return view == null ? 0 : move(view::extract, resource, amount, transaction);
	}

	@Override
	public int insert(N resource, int amount, TransactionContext transaction) {
		return move(storage::insert, resource, amount, transaction);
	}

	@Override
	public int extract(N resource, int amount, TransactionContext transaction) {
		return move(storage::extract, resource, amount, transaction);
	}

	/** Moves whole NeoForge units only: a fluid storage that moves part of a millibucket is asked again for less. */
	private int move(Move<F> move, N resource, int amount, TransactionContext neoTransaction) {
		if (resource.isEmpty() || amount <= 0) return 0;
		F variant = kind.fabric(resource);
		Transaction transaction = FabricTransactions.of(neoTransaction);
		long unit = kind.unit();
		if (unit == 1) return (int) move.apply(variant, amount, transaction);
		long moved;
		try (Transaction attempt = transaction.openNested()) {
			moved = move.apply(variant, amount * unit, attempt);
			if (moved % unit == 0) {
				attempt.commit();
				return (int) (moved / unit);
			}
		}
		long whole = moved - moved % unit;
		if (whole == 0) return 0;
		try (Transaction attempt = transaction.openNested()) {
			if (move.apply(variant, whole, attempt) != whole) return 0;
			attempt.commit();
			return (int) (whole / unit);
		}
	}
}
