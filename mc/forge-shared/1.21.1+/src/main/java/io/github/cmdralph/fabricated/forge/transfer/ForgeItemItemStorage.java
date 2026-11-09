package io.github.cmdralph.fabricated.forge.transfer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/**
 * A Forge mod's item handler item, a bag or a backpack, seen as a Fabric storage in a context, one view for each of the
 * handler's slots. As with {@link ForgeItemFluidStorage}, Forge's handler acts on a copy of one of the context's items,
 * and what it makes of the copy replaces that item in the context, in the transaction, so an aborted transaction never
 * reaches the item; a change that doesn't show in the item is refused, as the items would be lost: Fabric's item
 * variants carry the item's components, not Forge's capability data, where a handler may keep its items.
 */
final class ForgeItemItemStorage implements Storage<ItemVariant> {
	private final ContainerItemContext context;

	ForgeItemItemStorage(ContainerItemContext context) {
		this.context = context;
	}

	/** Forge's handler of {@code copy}, a copy of one of the context's items. */
	private static @Nullable IItemHandler handler(ItemStack copy) {
		return copy.isEmpty() ? null : copy.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
	}

	/** A copy of one of the context's items as it is now, or none. */
	private ItemStack copy() {
		ItemVariant item = context.getItemVariant();
		return item.isBlank() || context.getAmount() < 1 ? ItemStack.EMPTY : item.toStack(1);
	}

	@Override
	public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(resource, maxAmount);
		return change(transaction, handler -> {
			int slots = handler.getSlots();
			long inserted = 0;
			// Slots already holding the item first, then empty ones, as Forge's ItemHandlerHelper.insertItemStacked.
			for (int i = 0; i < slots && inserted < maxAmount; i++) {
				ItemStack held = handler.getStackInSlot(i);
				if (!held.isEmpty() && resource.matches(held)) inserted += insert(handler, i, resource, maxAmount - inserted);
			}
			for (int i = 0; i < slots && inserted < maxAmount; i++) {
				if (handler.getStackInSlot(i).isEmpty()) inserted += insert(handler, i, resource, maxAmount - inserted);
			}
			return inserted;
		});
	}

	/** Inserts up to {@code maxAmount} of the item into one slot, as much as it takes. */
	private static long insert(IItemHandler handler, int slot, ItemVariant resource, long maxAmount) {
		int asked = (int) Math.min(maxAmount, handler.getSlotLimit(slot));
		if (asked <= 0) return 0;
		ItemStack left = handler.insertItem(slot, resource.toStack(asked), false);
		return Math.max(asked - left.getCount(), 0);
	}

	@Override
	public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(resource, maxAmount);
		return change(transaction, handler -> {
			int slots = handler.getSlots();
			long extracted = 0;
			for (int i = 0; i < slots && extracted < maxAmount; i++) {
				long taken = extract(handler, i, resource, maxAmount - extracted);
				if (taken < 0) return 0;
				extracted += taken;
			}
			return extracted;
		});
	}

	/**
	 * Extracts up to {@code maxAmount} of the item from one slot, as much as it gives: -1 when it gives another item,
	 * which leaves the copy as no item should be.
	 */
	private static long extract(IItemHandler handler, int slot, ItemVariant resource, long maxAmount) {
		ItemStack held = handler.getStackInSlot(slot);
		if (held.isEmpty() || !resource.matches(held)) return 0;
		ItemStack taken = handler.extractItem(slot, (int) Math.min(maxAmount, held.getCount()), false);
		if (taken.isEmpty()) return 0;
		return resource.matches(taken) ? taken.getCount() : -1;
	}

	@FunctionalInterface
	private interface Move {
		long run(IItemHandler handler);
	}

	/** Moves items with Forge's handler of a copy of one of the context's items, and puts the copy in that item's place. */
	private long change(TransactionContext transaction, Move move) {
		ItemVariant before = context.getItemVariant();
		ItemStack copy = copy();
		IItemHandler handler = handler(copy);
		if (handler == null) return 0;
		long moved = move.run(handler);
		if (moved <= 0 || copy.getCount() != 1) return 0;
		ItemVariant after = ItemVariant.of(copy);
		if (after.equals(before)) return 0;
		return context.exchange(after, 1, transaction) == 1 ? moved : 0;
	}

	@Override
	public Iterator<StorageView<ItemVariant>> iterator() {
		IItemHandler handler = handler(copy());
		if (handler == null) return Collections.emptyIterator();
		int slots = handler.getSlots();
		List<StorageView<ItemVariant>> views = new ArrayList<>(slots);
		for (int i = 0; i < slots; i++) views.add(new Slot(i));
		return views.iterator();
	}

	/** A slot of the handler, read from a copy of the context's item as it is when asked. */
	private final class Slot implements StorageView<ItemVariant> {
		private final int index;

		Slot(int index) {
			this.index = index;
		}

		private ItemStack held() {
			IItemHandler handler = handler(copy());
			return handler == null || index >= handler.getSlots() ? ItemStack.EMPTY : handler.getStackInSlot(index);
		}

		@Override
		public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			StoragePreconditions.notBlankNotNegative(resource, maxAmount);
			return change(transaction, handler -> index < handler.getSlots() ? Math.max(ForgeItemItemStorage.extract(handler, index, resource, maxAmount), 0) : 0);
		}

		@Override
		public boolean isResourceBlank() {
			return held().isEmpty();
		}

		@Override
		public ItemVariant getResource() {
			ItemStack held = held();
			return held.isEmpty() ? ItemVariant.blank() : ItemVariant.of(held);
		}

		@Override
		public long getAmount() {
			return held().getCount();
		}

		@Override
		public long getCapacity() {
			ItemStack copy = copy();
			IItemHandler handler = handler(copy);
			if (handler == null || index >= handler.getSlots()) return 0;
			ItemStack held = handler.getStackInSlot(index);
			int limit = handler.getSlotLimit(index);
			return held.isEmpty() ? limit : Math.min(limit, held.getMaxStackSize());
		}
	}
}
