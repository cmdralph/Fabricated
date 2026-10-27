package net.fabricmc.fabric.api.transfer.v1.fluid.base;

import net.minecraft.class_1792;
import net.minecraft.class_3611;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

public final class EmptyItemFluidStorage implements Storage<FluidVariant> {
	public EmptyItemFluidStorage(ContainerItemContext context, class_1792 fullItem, class_3611 insertableFluid, long insertableAmount) {
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
