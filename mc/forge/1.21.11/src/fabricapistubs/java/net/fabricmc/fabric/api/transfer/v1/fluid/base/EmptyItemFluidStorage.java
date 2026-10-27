package net.fabricmc.fabric.api.transfer.v1.fluid.base;

import java.util.Iterator;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that the forge test mod calls, in Mojang's names.
 */
public final class EmptyItemFluidStorage implements Storage<FluidVariant> {
	public EmptyItemFluidStorage(ContainerItemContext context, Item fullItem, Fluid insertableFluid, long insertableAmount) {
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

	@Override
	public Iterator<StorageView<FluidVariant>> iterator() {
		throw new AssertionError("stand-in");
	}
}
