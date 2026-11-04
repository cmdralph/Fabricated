package io.github.cmdralph.fabricated.neoforge.transfer;

import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.EmptyItemHandler;

/**
 * A Fabric mod's item storage in an item, a bag or a backpack, seen as NeoForge 21.1's item handler of the item. As
 * with {@link FabricItemFluidHandler}, the context is a slot holding the container ({@link ContainerSlot}), the storage
 * is found anew for each call, and the same item with other components is written into the container. NeoForge's
 * handler of an item has no way to hand back another stack, as a fluid handler's container does, so a move that would
 * leave another item, or a different count of it, in the container's place doesn't happen: the items moved would be
 * lost to a caller holding the container.
 */
final class FabricItemItemHandler implements IItemHandler {
	private final ContainerItemContext context;
	/** Whether a move leaves the container's item in its place, which it must for the move to stand. */
	private final BooleanSupplier holdsContainer;

	private FabricItemItemHandler(ItemStack container) {
		ContainerSlot slot = new ContainerSlot(container);
		context = ContainerItemContext.ofSingleSlot(slot);
		holdsContainer = slot::holdsContainer;
	}

	/** The handler of a Fabric mod's item, if Fabric's lookup has a storage for it. */
	static @Nullable FabricItemItemHandler of(ItemStack container) {
		FabricItemItemHandler handler = new FabricItemItemHandler(container);
		return handler.storage() == null ? null : handler;
	}

	/** Fabric's storage for the container as it is now, if it has one. */
	@Nullable Storage<ItemVariant> storage() {
		return context.find(ItemStorage.ITEM);
	}

	private IItemHandler handler() {
		Storage<ItemVariant> storage = storage();
		return storage == null ? EmptyItemHandler.INSTANCE : new FabricItemHandler(storage, holdsContainer);
	}

	@Override
	public int getSlots() {
		return handler().getSlots();
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		return handler().getStackInSlot(slot);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		return handler().insertItem(slot, stack, simulate);
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		return handler().extractItem(slot, amount, simulate);
	}

	@Override
	public int getSlotLimit(int slot) {
		return handler().getSlotLimit(slot);
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return handler().isItemValid(slot, stack);
	}
}
