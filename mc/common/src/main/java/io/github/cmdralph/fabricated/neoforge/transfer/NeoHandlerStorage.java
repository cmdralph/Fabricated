package io.github.cmdralph.fabricated.neoforge.transfer;

import java.util.Iterator;

import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

/** A NeoForge resource handler seen as a Fabric storage, index for slot. */
final class NeoHandlerStorage<F extends TransferVariant<?>, N extends Resource> implements SlottedStorage<F> {
	final ResourceHandler<N> handler;
	private final ResourceKind<F, N> kind;

	NeoHandlerStorage(ResourceHandler<N> handler, ResourceKind<F, N> kind) {
		this.handler = handler;
		this.kind = kind;
	}

	@Override
	public long insert(F resource, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(resource, maxAmount);
		return handler.insert(kind.neo(resource), kind.toNeoAmount(maxAmount), NeoTransactions.of(transaction)) * kind.unit();
	}

	@Override
	public long extract(F resource, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(resource, maxAmount);
		return handler.extract(kind.neo(resource), kind.toNeoAmount(maxAmount), NeoTransactions.of(transaction)) * kind.unit();
	}

	@Override
	public int getSlotCount() {
		return handler.size();
	}

	@Override
	public SingleSlotStorage<F> getSlot(int slot) {
		return new Slot(slot);
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"}) // A slot is a view.
	public Iterator<StorageView<F>> iterator() {
		return (Iterator) getSlots().iterator();
	}

	private final class Slot implements SingleSlotStorage<F> {
		private final int index;

		Slot(int index) {
			this.index = index;
		}

		@Override
		public long insert(F resource, long maxAmount, TransactionContext transaction) {
			StoragePreconditions.notBlankNotNegative(resource, maxAmount);
			return handler.insert(index, kind.neo(resource), kind.toNeoAmount(maxAmount), NeoTransactions.of(transaction)) * kind.unit();
		}

		@Override
		public long extract(F resource, long maxAmount, TransactionContext transaction) {
			StoragePreconditions.notBlankNotNegative(resource, maxAmount);
			return handler.extract(index, kind.neo(resource), kind.toNeoAmount(maxAmount), NeoTransactions.of(transaction)) * kind.unit();
		}

		@Override
		public boolean isResourceBlank() {
			return handler.getResource(index).isEmpty();
		}

		@Override
		public F getResource() {
			return kind.fabric(handler.getResource(index));
		}

		@Override
		public long getAmount() {
			return handler.getAmountAsLong(index) * kind.unit();
		}

		@Override
		public long getCapacity() {
			return handler.getCapacityAsLong(index, handler.getResource(index)) * kind.unit();
		}
	}
}
