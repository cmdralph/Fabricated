package io.github.cmdralph.fabricated.neoforge.transfer;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A Fabric mod's item storage seen as a NeoForge item handler. A slotted storage maps slot for slot; another storage exposes
 * its views as slots, with insertion going to the storage as a whole through slot 0.
 */
final class FabricItemHandler implements IItemHandler {
	final Storage<ItemVariant> storage;

	FabricItemHandler(Storage<ItemVariant> storage) {
		this.storage = storage;
	}

	@Override
	public int getSlots() {
		if (storage instanceof SlottedStorage<ItemVariant> slotted) return slotted.getSlotCount();
		int size = 0;
		for (StorageView<ItemVariant> ignored : storage) size++;
		return Math.max(size, 1);
	}

	private @Nullable StorageView<ItemVariant> view(int slot) {
		if (storage instanceof SlottedStorage<ItemVariant> slotted) return slot >= 0 && slot < slotted.getSlotCount() ? slotted.getSlot(slot) : null;
		int i = 0;
		for (StorageView<ItemVariant> view : storage) {
			if (i++ == slot) return view;
		}
		return null;
	}

	/** Where an insertion into {@code slot} goes. */
	private @Nullable Storage<ItemVariant> target(int slot) {
		if (storage instanceof SlottedStorage<ItemVariant> slotted) return slot >= 0 && slot < slotted.getSlotCount() ? slotted.getSlot(slot) : null;
		return slot == 0 ? storage : null;
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		StorageView<ItemVariant> view = view(slot);
		if (view == null || view.isResourceBlank()) return ItemStack.EMPTY;
		return view.getResource().toStack((int) Math.min(view.getAmount(), Integer.MAX_VALUE));
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		if (stack.isEmpty()) return ItemStack.EMPTY;
		Storage<ItemVariant> target = target(slot);
		if (target == null) return stack;
		ItemVariant variant = ItemVariant.of(stack);
		long inserted = FabricTransactions.run(simulate, transaction -> target.insert(variant, stack.getCount(), transaction));
		if (inserted == 0) return stack;
		return inserted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - (int) inserted);
	}

	/** At most a stack of the item, as NeoForge's own handlers extract. */
	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		if (amount <= 0) return ItemStack.EMPTY;
		StorageView<ItemVariant> view = view(slot);
		if (view == null || view.isResourceBlank()) return ItemStack.EMPTY;
		ItemVariant variant = view.getResource();
		int max = Math.min(amount, variant.toStack(1).getMaxStackSize());
		long extracted = FabricTransactions.run(simulate, transaction -> view.extract(variant, max, transaction));
		return extracted == 0 ? ItemStack.EMPTY : variant.toStack((int) extracted);
	}

	@Override
	public int getSlotLimit(int slot) {
		StorageView<ItemVariant> view = view(slot);
		return view == null ? 64 : (int) Math.min(view.getCapacity(), Integer.MAX_VALUE);
	}

	/** Whether the slot takes items at all: Fabric's storages tell which only by trying, which {@link #insertItem} does. */
	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		Storage<ItemVariant> target = target(slot);
		return target != null && target.supportsInsertion();
	}
}
