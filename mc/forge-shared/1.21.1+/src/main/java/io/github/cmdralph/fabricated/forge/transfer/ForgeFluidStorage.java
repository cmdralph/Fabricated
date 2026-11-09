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

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * A Forge mod's fluid handler seen as a Fabric storage, its tanks as views. As {@link ForgeItemStorage}'s slots, it only
 * simulates while a transaction is open, filling or draining the handler once the outermost one commits; Forge fills and
 * drains a handler as a whole, by fluid, so what is pending is the handler's: one fluid, one way, per transaction. Its
 * tanks show the handler's contents without what is pending, and extracting through a tank drains that fluid from the
 * handler, from whichever of its tanks Forge's handler drains. Amounts move in whole millibuckets, and a fluid carrying
 * components doesn't move ({@link FluidUnits#crosses}).
 */
final class ForgeFluidStorage extends SnapshotParticipant<ForgeFluidStorage.Pending> implements Storage<FluidVariant> {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** The storage of each handler, for as long as either is in use: a transaction holds the storages it changed. */
	private static final Map<IFluidHandler, ForgeFluidStorage> STORAGES = new MapMaker().weakKeys().weakValues().makeMap();
	/** The handler classes already logged as moving less than they promised. */
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	final IFluidHandler handler;
	private FluidVariant variant = FluidVariant.blank();
	/** Millibuckets pending: filled (positive) or drained (negative). */
	private long delta;

	private ForgeFluidStorage(IFluidHandler handler) {
		this.handler = handler;
	}

	static ForgeFluidStorage of(IFluidHandler handler) {
		return STORAGES.computeIfAbsent(handler, ForgeFluidStorage::new);
	}

	/** What the handler has pending in the open transactions. */
	record Pending(FluidVariant variant, long delta) {
	}

	@Override
	public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(resource, maxAmount);
		if (!FluidUnits.crosses(resource) || delta < 0 || delta > 0 && !resource.equals(variant)) return 0;
		long millibuckets = maxAmount / FluidUnits.DROPLETS;
		int asked = (int) Math.min(delta + millibuckets, Integer.MAX_VALUE);
		if (asked <= delta) return 0;
		long filled = Math.min(handler.fill(FluidUnits.stack(resource, asked), IFluidHandler.FluidAction.SIMULATE) - delta, millibuckets);
		if (filled <= 0) return 0;
		updateSnapshots(transaction);
		delta += filled;
		variant = resource;
		return filled * FluidUnits.DROPLETS;
	}

	@Override
	public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(resource, maxAmount);
		if (!FluidUnits.crosses(resource) || delta > 0 || delta < 0 && !resource.equals(variant)) return 0;
		long millibuckets = maxAmount / FluidUnits.DROPLETS;
		long pending = -delta;
		int asked = (int) Math.min(pending + millibuckets, Integer.MAX_VALUE);
		if (asked <= pending) return 0;
		FluidStack drained = handler.drain(FluidUnits.stack(resource, asked), IFluidHandler.FluidAction.SIMULATE);
		if (drained.isEmpty() || !FluidUnits.variant(drained).equals(resource)) return 0;
		long extracted = Math.min(drained.getAmount() - pending, millibuckets);
		if (extracted <= 0) return 0;
		updateSnapshots(transaction);
		delta -= extracted;
		variant = resource;
		return extracted * FluidUnits.DROPLETS;
	}

	@Override
	public Iterator<StorageView<FluidVariant>> iterator() {
		int tanks = handler.getTanks();
		List<StorageView<FluidVariant>> views = new ArrayList<>(tanks);
		for (int i = 0; i < tanks; i++) views.add(new Tank(i));
		return views.iterator();
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

	/** Fills or drains what the committed transactions left pending, now that none is open. */
	@Override
	protected void onFinalCommit() {
		FluidVariant moved = variant;
		long amount = delta;
		variant = FluidVariant.blank();
		delta = 0;
		if (amount > 0) {
			if (handler.fill(FluidUnits.stack(moved, (int) amount), IFluidHandler.FluidAction.EXECUTE) < amount) warn("took less fluid");
		} else if (amount < 0) {
			if (handler.drain(FluidUnits.stack(moved, (int) -amount), IFluidHandler.FluidAction.EXECUTE).getAmount() < -amount) warn("gave less fluid");
		}
	}

	private void warn(String what) {
		if (WARNED.add(handler.getClass().getName())) {
			LOGGER.warn("Forge fluid handler {} {} than it said it would when simulating; Fabric's transaction had already committed", handler.getClass().getName(), what);
		}
	}

	private final class Tank implements StorageView<FluidVariant> {
		private final int index;

		Tank(int index) {
			this.index = index;
		}

		@Override
		public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
			return resource.equals(getResource()) ? ForgeFluidStorage.this.extract(resource, maxAmount, transaction) : 0;
		}

		@Override
		public boolean isResourceBlank() {
			return handler.getFluidInTank(index).isEmpty();
		}

		@Override
		public FluidVariant getResource() {
			return FluidUnits.variant(handler.getFluidInTank(index));
		}

		@Override
		public long getAmount() {
			return handler.getFluidInTank(index).getAmount() * FluidUnits.DROPLETS;
		}

		@Override
		public long getCapacity() {
			return handler.getTankCapacity(index) * FluidUnits.DROPLETS;
		}
	}
}
