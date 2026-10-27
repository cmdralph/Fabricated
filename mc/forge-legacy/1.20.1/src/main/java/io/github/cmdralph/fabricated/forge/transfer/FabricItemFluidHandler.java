package io.github.cmdralph.fabricated.forge.transfer;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.EmptyFluidHandler;

/**
 * A Fabric mod's fluid storage in an item, seen as Forge's fluid handler of the item. Forge's handler acts on its
 * container, the stack it belongs to, and Fabric's storage on the item in a context, so the context here is a slot
 * holding the container; the storage is found anew for each call, as the item may have changed since. Fabric's storage
 * changes the item by replacing it: a different item becomes the container, as with Forge's own bucket handler, and the
 * same item with other data is written into the container, as Forge's own tank handlers change the stack in place, so a
 * Forge mod filling the item where it lies sees the change without taking {@link #getContainer} back.
 */
final class FabricItemFluidHandler implements IFluidHandlerItem {
	private final ContainerSlot slot;
	private final ContainerItemContext context;

	FabricItemFluidHandler(ItemStack container) {
		slot = new ContainerSlot(container);
		context = ContainerItemContext.ofSingleSlot(slot);
	}

	/** Fabric's storage for the container as it is now, if it has one. */
	@Nullable Storage<FluidVariant> storage() {
		return context.find(FluidStorage.ITEM);
	}

	private IFluidHandler handler() {
		Storage<FluidVariant> storage = storage();
		return storage == null ? EmptyFluidHandler.INSTANCE : new FabricFluidHandler(storage);
	}

	@Override
	public ItemStack getContainer() {
		return slot.stack;
	}

	@Override
	public int getTanks() {
		return handler().getTanks();
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		return handler().getFluidInTank(tank);
	}

	@Override
	public int getTankCapacity(int tank) {
		return handler().getTankCapacity(tank);
	}

	@Override
	public boolean isFluidValid(int tank, FluidStack stack) {
		return handler().isFluidValid(tank, stack);
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		return handler().fill(resource, action);
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		return handler().drain(resource, action);
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		return handler().drain(maxDrain, action);
	}

	/**
	 * The slot holding the container. Fabric's slot takes a snapshot by putting a copy of the stack in its place, so a
	 * transaction never changes the stack it began with; once the outermost one commits, an item that stayed the same is
	 * written into that stack, which stays the container.
	 */
	private static final class ContainerSlot extends SingleStackStorage {
		private ItemStack stack;
		/** The container when no transaction is open: the stack Forge's caller holds, unless it was replaced. */
		private ItemStack committed;

		ContainerSlot(ItemStack container) {
			stack = container;
			committed = container;
		}

		@Override
		protected ItemStack getStack() {
			return stack;
		}

		@Override
		protected void setStack(ItemStack stack) {
			this.stack = stack;
		}

		@Override
		protected void onFinalCommit() {
			if (stack != committed && !committed.isEmpty() && ItemStack.isSameItem(committed, stack)) {
				committed.setTag(stack.getTag());
				committed.setCount(stack.getCount());
				stack = committed;
			}
			committed = stack;
		}
	}
}
