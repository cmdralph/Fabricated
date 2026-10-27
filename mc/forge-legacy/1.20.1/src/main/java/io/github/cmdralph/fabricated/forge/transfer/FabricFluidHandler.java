package io.github.cmdralph.fabricated.forge.transfer;

import java.util.Iterator;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * A Fabric mod's fluid storage seen as a Forge fluid handler, its views as tanks. Forge fills and drains a handler as a
 * whole, by fluid, as Fabric inserts into and extracts from a storage; a storage that moves part of a millibucket is
 * asked again for the whole millibuckets of it.
 */
final class FabricFluidHandler implements IFluidHandler {
	final Storage<FluidVariant> storage;

	FabricFluidHandler(Storage<FluidVariant> storage) {
		this.storage = storage;
	}

	@FunctionalInterface
	private interface Move {
		long apply(FluidVariant variant, long maxAmount, Transaction transaction);
	}

	@Override
	public int getTanks() {
		if (storage instanceof SlottedStorage<FluidVariant> slotted) return slotted.getSlotCount();
		int size = 0;
		for (StorageView<FluidVariant> ignored : storage) size++;
		return Math.max(size, 1);
	}

	private @Nullable StorageView<FluidVariant> view(int tank) {
		if (storage instanceof SlottedStorage<FluidVariant> slotted) return tank >= 0 && tank < slotted.getSlotCount() ? slotted.getSlot(tank) : null;
		int i = 0;
		for (StorageView<FluidVariant> view : storage) {
			if (i++ == tank) return view;
		}
		return null;
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		StorageView<FluidVariant> view = view(tank);
		if (view == null || view.isResourceBlank() || view.getAmount() < FluidUnits.DROPLETS) return FluidStack.EMPTY;
		return FluidUnits.stack(view.getResource(), FluidUnits.millibuckets(view.getAmount()));
	}

	@Override
	public int getTankCapacity(int tank) {
		StorageView<FluidVariant> view = view(tank);
		return view == null ? 0 : FluidUnits.millibuckets(view.getCapacity());
	}

	/** Any fluid: Fabric's storages tell which they take only by trying, which {@link #fill} does. */
	@Override
	public boolean isFluidValid(int tank, FluidStack stack) {
		return true;
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		if (resource.isEmpty()) return 0;
		return (int) move(storage::insert, FluidUnits.variant(resource), resource.getAmount(), action.simulate());
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		if (resource.isEmpty()) return FluidStack.EMPTY;
		long drained = move(storage::extract, FluidUnits.variant(resource), resource.getAmount(), action.simulate());
		return drained == 0 ? FluidStack.EMPTY : new FluidStack(resource, (int) drained);
	}

	/** The first fluid the storage holds, as Forge's own handlers drain by amount. */
	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		if (maxDrain <= 0) return FluidStack.EMPTY;
		Iterator<StorageView<FluidVariant>> views = storage.nonEmptyViews().iterator();
		if (!views.hasNext()) return FluidStack.EMPTY;
		FluidVariant variant = views.next().getResource();
		long drained = move(storage::extract, variant, maxDrain, action.simulate());
		return drained == 0 ? FluidStack.EMPTY : FluidUnits.stack(variant, (int) drained);
	}

	/** Moves whole millibuckets only, returning how many. */
	private static long move(Move move, FluidVariant variant, int millibuckets, boolean simulate) {
		return FabricTransactions.run(simulate, transaction -> {
			long moved;
			try (Transaction attempt = transaction.openNested()) {
				moved = move.apply(variant, millibuckets * FluidUnits.DROPLETS, attempt);
				if (moved % FluidUnits.DROPLETS == 0) {
					attempt.commit();
					return moved / FluidUnits.DROPLETS;
				}
			}
			long whole = moved - moved % FluidUnits.DROPLETS;
			if (whole == 0) return 0;
			try (Transaction attempt = transaction.openNested()) {
				if (move.apply(variant, whole, attempt) != whole) return 0;
				attempt.commit();
				return whole / FluidUnits.DROPLETS;
			}
		});
	}
}
