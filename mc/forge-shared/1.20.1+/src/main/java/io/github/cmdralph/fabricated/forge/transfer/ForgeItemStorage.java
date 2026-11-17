package io.github.cmdralph.fabricated.forge.transfer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.MapMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.IItemHandler;

/**
 * A Forge mod's item handler seen as a Fabric storage, slot for slot. While a transaction is open, each slot only asks
 * the handler, with Forge's simulate flag, how much of everything moved in it so far it would take or give, and keeps
 * the difference; once the outermost transaction commits, it moves that for real. An aborted transaction never reaches
 * the handler, so a Forge mod's machine isn't told of contents that were never there.
 *
 * <p>A slot moves one way per transaction: one with an insertion pending refuses an extraction until the transaction
 * closes, and the other way round. A handler is seen through one storage, so two lookups of the same block in one
 * transaction share what is pending ({@link #of}); two handlers a Forge mod hands out for the same inventory (one per
 * side) don't, and could each promise the same items, which the second then fails to move. Moving them is logged, and
 * items a handler then refuses to take are dropped at its block rather than lost.
 */
final class ForgeItemStorage implements SlottedStorage<ItemVariant> {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** The storage of each handler, for as long as either is in use: a transaction holds the storages it changed. */
	private static final Map<IItemHandler, ForgeItemStorage> STORAGES = new MapMaker().weakKeys().weakValues().makeMap();
	/** The handler classes already logged as moving less than they promised. */
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	final IItemHandler handler;
	private final Level level;
	private final BlockPos pos;
	private final List<Slot> slots = new ArrayList<>();

	private ForgeItemStorage(IItemHandler handler, Level level, BlockPos pos) {
		this.handler = handler;
		this.level = level;
		this.pos = pos.immutable();
	}

	/** The storage for {@code handler}, a block entity's at {@code pos}. */
	static ForgeItemStorage of(IItemHandler handler, Level level, BlockPos pos) {
		return STORAGES.computeIfAbsent(handler, key -> new ForgeItemStorage(key, level, pos));
	}

	@Override
	public int getSlotCount() {
		return handler.getSlots();
	}

	@Override
	public SingleSlotStorage<ItemVariant> getSlot(int index) {
		if (index < 0 || index >= handler.getSlots()) throw new IndexOutOfBoundsException(index);
		return slot(index);
	}

	private Slot slot(int index) {
		synchronized (slots) {
			while (slots.size() <= index) slots.add(new Slot(slots.size()));
			return slots.get(index);
		}
	}

	@Override
	public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(resource, maxAmount);
		int count = handler.getSlots();
		long inserted = 0;
		// Slots already holding the item first, then empty ones, as Forge's ItemHandlerHelper.insertItemStacked.
		for (int i = 0; i < count && inserted < maxAmount; i++) {
			Slot slot = slot(i);
			if (slot.getAmount() > 0 && slot.getResource().equals(resource)) inserted += slot.insert(resource, maxAmount - inserted, transaction);
		}
		for (int i = 0; i < count && inserted < maxAmount; i++) {
			Slot slot = slot(i);
			if (slot.getAmount() == 0) inserted += slot.insert(resource, maxAmount - inserted, transaction);
		}
		return inserted;
	}

	@Override
	public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(resource, maxAmount);
		int count = handler.getSlots();
		long extracted = 0;
		for (int i = 0; i < count && extracted < maxAmount; i++) extracted += slot(i).extract(resource, maxAmount - extracted, transaction);
		return extracted;
	}

	@Override
	public Iterator<StorageView<ItemVariant>> iterator() {
		int count = handler.getSlots();
		List<StorageView<ItemVariant>> views = new ArrayList<>(count);
		for (int i = 0; i < count; i++) views.add(slot(i));
		return views.iterator();
	}

	private void warn(String what) {
		if (WARNED.add(handler.getClass().getName())) {
			LOGGER.warn("Forge item handler {} at {} {} than it said it would when simulating; Fabric's transaction had already committed", handler.getClass().getName(), pos, what);
		}
	}

	/** What a slot has pending in the open transactions: an item and how many go in (positive) or come out (negative). */
	private record Pending(ItemVariant variant, long delta) {
	}

	private final class Slot extends SnapshotParticipant<Pending> implements SingleSlotStorage<ItemVariant> {
		private final int index;
		private ItemVariant variant = ItemVariant.blank();
		private long delta;

		Slot(int index) {
			this.index = index;
		}

		@Override
		public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			StoragePreconditions.notBlankNotNegative(resource, maxAmount);
			if (delta < 0 || delta > 0 && !resource.equals(variant)) return 0;
			ItemStack current = handler.getStackInSlot(index);
			if (!current.isEmpty() && !resource.matches(current)) return 0;
			// Everything pending and the new amount at once, up to what the slot can hold at all.
			int asked = (int) Math.min(delta + maxAmount, handler.getSlotLimit(index));
			if (asked <= delta) return 0;
			ItemStack left = handler.insertItem(index, resource.toStack(asked), true);
			long accepted = Math.min(asked - left.getCount() - delta, maxAmount);
			if (accepted <= 0) return 0;
			updateSnapshots(transaction);
			delta += accepted;
			variant = resource;
			return accepted;
		}

		@Override
		public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			StoragePreconditions.notBlankNotNegative(resource, maxAmount);
			if (delta > 0) return 0;
			ItemStack current = handler.getStackInSlot(index);
			if (current.isEmpty() || !resource.matches(current)) return 0;
			long pending = -delta;
			int asked = (int) Math.min(pending + maxAmount, current.getCount());
			if (asked <= pending) return 0;
			ItemStack taken = handler.extractItem(index, asked, true);
			if (!taken.isEmpty() && !resource.matches(taken)) return 0;
			long extracted = Math.min(taken.getCount() - pending, maxAmount);
			if (extracted <= 0) return 0;
			updateSnapshots(transaction);
			delta -= extracted;
			variant = resource;
			return extracted;
		}

		@Override
		public boolean isResourceBlank() {
			return getAmount() == 0;
		}

		@Override
		public ItemVariant getResource() {
			ItemStack current = handler.getStackInSlot(index);
			if (current.isEmpty()) return delta > 0 ? variant : ItemVariant.blank();
			return current.getCount() + delta > 0 ? ItemVariant.of(current) : ItemVariant.blank();
		}

		@Override
		public long getAmount() {
			return Math.max(handler.getStackInSlot(index).getCount() + delta, 0);
		}

		@Override
		public long getCapacity() {
			ItemVariant resource = getResource();
			int limit = handler.getSlotLimit(index);
			return resource.isBlank() ? limit : Math.min(limit, resource.toStack(1).getMaxStackSize());
		}

		@Override
		protected Pending createSnapshot() {
			return new Pending(variant, delta);
		}

		@Override
		protected void readSnapshot(Pending snapshot) {
			variant = snapshot.variant();
			delta = snapshot.delta();
		}

		/** Moves what the committed transactions left pending, now that none is open. */
		@Override
		protected void onFinalCommit() {
			ItemVariant moved = variant;
			long amount = delta;
			variant = ItemVariant.blank();
			delta = 0;
			if (amount > 0) {
				ItemStack left = handler.insertItem(index, moved.toStack((int) amount), false);
				if (!left.isEmpty()) {
					warn("took fewer items");
					Block.popResource(level, pos, left);
				}
			} else if (amount < 0) {
				ItemStack taken = handler.extractItem(index, (int) -amount, false);
				if (taken.getCount() < -amount) warn("gave fewer items");
			}
		}
	}
}
