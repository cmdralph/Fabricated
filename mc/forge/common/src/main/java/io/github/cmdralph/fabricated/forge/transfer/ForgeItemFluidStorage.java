package io.github.cmdralph.fabricated.forge.transfer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

/**
 * A Forge mod's fluid handler item seen as a Fabric storage in a context. Forge's handler changes the stack it acts on;
 * here it acts on a copy of one of the context's items, and what it makes of the copy replaces that item in the context,
 * in the transaction, so an aborted transaction never reaches the item. A change that doesn't show in the item is
 * refused, as the fluid would be lost: Fabric's item variants carry the item's components, not Forge's capability data,
 * where a handler may keep its fluid. Amounts move in whole millibuckets, and a fluid carrying data doesn't move
 * ({@link FluidUnits#crosses}).
 */
final class ForgeItemFluidStorage implements Storage<FluidVariant> {
	private final ContainerItemContext context;

	ForgeItemFluidStorage(ContainerItemContext context) {
		this.context = context;
	}

	/** Forge's handler of one of the context's items as it is now, acting on a copy. */
	private @Nullable IFluidHandlerItem handler() {
		ItemVariant item = context.getItemVariant();
		if (item.isBlank() || context.getAmount() < 1) return null;
		return item.toStack(1).getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
	}

	@Override
	public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(resource, maxAmount);
		int millibuckets = FluidUnits.millibuckets(maxAmount);
		IFluidHandlerItem handler = FluidUnits.crosses(resource) ? handler() : null;
		if (handler == null || millibuckets == 0) return 0;
		int filled = handler.fill(FluidUnits.stack(resource, millibuckets), IFluidHandler.FluidAction.EXECUTE);
		if (filled <= 0 || filled > millibuckets || !replace(handler, transaction)) return 0;
		return filled * FluidUnits.DROPLETS;
	}

	@Override
	public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(resource, maxAmount);
		int millibuckets = FluidUnits.millibuckets(maxAmount);
		IFluidHandlerItem handler = FluidUnits.crosses(resource) ? handler() : null;
		if (handler == null || millibuckets == 0) return 0;
		FluidStack drained = handler.drain(FluidUnits.stack(resource, millibuckets), IFluidHandler.FluidAction.EXECUTE);
		if (drained.isEmpty() || drained.getAmount() > millibuckets || !FluidUnits.crosses(drained) || !FluidUnits.variant(drained).equals(resource)) return 0;
		if (!replace(handler, transaction)) return 0;
		return drained.getAmount() * FluidUnits.DROPLETS;
	}

	/** Puts what the handler made of its copy in place of one of the context's items. */
	private boolean replace(IFluidHandlerItem handler, TransactionContext transaction) {
		ItemVariant before = context.getItemVariant();
		ItemStack result = handler.getContainer();
		// A container used up, as some single-use ones are.
		if (result.isEmpty()) return context.extract(before, 1, transaction) == 1;
		ItemVariant after = ItemVariant.of(result);
		if (result.getCount() != 1 || after.equals(before)) return false;
		return context.exchange(after, 1, transaction) == 1;
	}

	@Override
	public Iterator<StorageView<FluidVariant>> iterator() {
		IFluidHandlerItem handler = handler();
		if (handler == null) return Collections.emptyIterator();
		int tanks = handler.getTanks();
		List<StorageView<FluidVariant>> views = new ArrayList<>(tanks);
		for (int i = 0; i < tanks; i++) views.add(new Tank(i));
		return views.iterator();
	}

	/** A tank of the handler, read from a copy of the context's item as it is when asked. */
	private final class Tank implements StorageView<FluidVariant> {
		private final int index;

		Tank(int index) {
			this.index = index;
		}

		private FluidStack fluid() {
			IFluidHandlerItem handler = handler();
			return handler == null || index >= handler.getTanks() ? FluidStack.EMPTY : handler.getFluidInTank(index);
		}

		@Override
		public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
			return resource.equals(getResource()) ? ForgeItemFluidStorage.this.extract(resource, maxAmount, transaction) : 0;
		}

		@Override
		public boolean isResourceBlank() {
			return fluid().isEmpty();
		}

		@Override
		public FluidVariant getResource() {
			return FluidUnits.variant(fluid());
		}

		@Override
		public long getAmount() {
			return fluid().getAmount() * FluidUnits.DROPLETS;
		}

		@Override
		public long getCapacity() {
			IFluidHandlerItem handler = handler();
			return handler == null || index >= handler.getTanks() ? 0 : handler.getTankCapacity(index) * FluidUnits.DROPLETS;
		}
	}
}
