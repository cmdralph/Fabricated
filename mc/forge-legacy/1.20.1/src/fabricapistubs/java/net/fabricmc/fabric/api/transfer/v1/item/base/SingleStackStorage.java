package net.fabricmc.fabric.api.transfer.v1.item.base;

import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

/**
 * Compile-time stand-in: the members of Fabric API's 1.20.1 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public abstract class SingleStackStorage extends SnapshotParticipant<ItemStack> implements SingleSlotStorage<ItemVariant> {
	protected abstract ItemStack getStack();

	protected abstract void setStack(ItemStack stack);

	@Override
	public boolean isResourceBlank() {
		throw new AssertionError("stand-in");
	}

	@Override
	public ItemVariant getResource() {
		throw new AssertionError("stand-in");
	}

	@Override
	public long getAmount() {
		throw new AssertionError("stand-in");
	}

	@Override
	public long getCapacity() {
		throw new AssertionError("stand-in");
	}

	@Override
	public long insert(ItemVariant insertedVariant, long maxAmount, TransactionContext transaction) {
		throw new AssertionError("stand-in");
	}

	@Override
	public long extract(ItemVariant variant, long maxAmount, TransactionContext transaction) {
		throw new AssertionError("stand-in");
	}

	@Override
	protected ItemStack createSnapshot() {
		throw new AssertionError("stand-in");
	}

	@Override
	protected void readSnapshot(ItemStack snapshot) {
		throw new AssertionError("stand-in");
	}
}
