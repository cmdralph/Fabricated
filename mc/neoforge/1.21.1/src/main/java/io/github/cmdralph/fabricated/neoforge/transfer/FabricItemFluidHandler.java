package io.github.cmdralph.fabricated.neoforge.transfer;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.EmptyFluidHandler;

/**
 * A Fabric mod's fluid storage in an item, seen as NeoForge's fluid handler of the item. NeoForge's handler acts on its
 * container, the stack it belongs to, and Fabric's storage on the item in a context, so the context here is a slot
 * holding the container ({@link ContainerSlot}); the storage is found anew for each call, as the item may have changed
 * since. Fabric's storage changes the item by replacing it: a different item becomes the container, as with NeoForge's
 * own bucket handler, and the same item with other components is written into the container, as NeoForge's own tank
 * handlers change the stack in place, so a NeoForge mod filling the item where it lies sees the change without taking
 * {@link #getContainer} back.
 */
final class FabricItemFluidHandler implements IFluidHandlerItem {
	private final ContainerSlot slot;
	private final ContainerItemContext context;

	private FabricItemFluidHandler(ItemStack container) {
		slot = new ContainerSlot(container);
		context = ContainerItemContext.ofSingleSlot(slot);
	}

	/** The handler of a Fabric mod's item, if Fabric's lookup has a storage for it. */
	static @Nullable FabricItemFluidHandler of(ItemStack container) {
		FabricItemFluidHandler handler = new FabricItemFluidHandler(container);
		return handler.storage() == null ? null : handler;
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
		return slot.stack();
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
}
