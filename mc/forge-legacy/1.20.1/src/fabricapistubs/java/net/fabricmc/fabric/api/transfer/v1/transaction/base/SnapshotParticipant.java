package net.fabricmc.fabric.api.transfer.v1.transaction.base;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/**
 * Compile-time stand-in: the members of Fabric API's 1.20.1 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public abstract class SnapshotParticipant<T> {
	protected abstract T createSnapshot();

	protected abstract void readSnapshot(T snapshot);

	protected void onFinalCommit() {
		throw new AssertionError("stand-in");
	}

	public void updateSnapshots(TransactionContext transaction) {
		throw new AssertionError("stand-in");
	}
}
