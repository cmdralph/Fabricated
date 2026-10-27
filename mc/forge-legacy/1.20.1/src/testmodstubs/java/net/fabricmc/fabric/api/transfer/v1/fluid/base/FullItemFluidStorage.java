package net.fabricmc.fabric.api.transfer.v1.fluid.base;

import net.minecraft.class_1792;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

public final class FullItemFluidStorage implements Storage<FluidVariant> {
	public FullItemFluidStorage(ContainerItemContext context, class_1792 emptyItem, FluidVariant containedFluid, long containedAmount) {
		throw new AssertionError("stand-in");
	}

	@Override
	public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
		throw new AssertionError("stand-in");
	}

	@Override
	public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
		throw new AssertionError("stand-in");
	}
}
