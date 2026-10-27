package net.fabricmc.fabric.api.transfer.v1.item.base;

import net.minecraft.class_1799;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

public abstract class SingleStackStorage implements SingleSlotStorage<ItemVariant> {
	protected abstract class_1799 getStack();

	protected abstract void setStack(class_1799 stack);

	@Override
	public long insert(ItemVariant insertedVariant, long maxAmount, TransactionContext transaction) {
		throw new AssertionError("stand-in");
	}

	@Override
	public long extract(ItemVariant variant, long maxAmount, TransactionContext transaction) {
		throw new AssertionError("stand-in");
	}
}
