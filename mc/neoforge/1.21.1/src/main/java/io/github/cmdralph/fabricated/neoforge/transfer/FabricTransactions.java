package io.github.cmdralph.fabricated.neoforge.transfer;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

/** Fabric transactions for NeoForge 21.1's handler calls, which move at once or only simulate. */
final class FabricTransactions {
	private FabricTransactions() {
	}

	@FunctionalInterface
	interface Action {
		long run(Transaction transaction);
	}

	/**
	 * Runs {@code action} in a transaction of its own, committed unless {@code simulate}: an outer one, or one nested in
	 * the transaction open on this thread (a Fabric mod's code calling NeoForge's while it moves resources), which then
	 * commits or aborts it with the rest. Nothing moves in a transaction's close callbacks, where none can be opened
	 * (a NeoForge handler called as a {@link NeoItemStorage}'s moves are applied).
	 */
	static long run(boolean simulate, Action action) {
		Transaction transaction = open();
		if (transaction == null) return 0;
		try (transaction) {
			long moved = action.run(transaction);
			if (!simulate) transaction.commit();
			return moved;
		}
	}

	private static @Nullable Transaction open() {
		Transaction.Lifecycle lifecycle = Transaction.getLifecycle();
		if (lifecycle == Transaction.Lifecycle.NONE) return Transaction.openOuter();
		if (lifecycle == Transaction.Lifecycle.OPEN) return Transaction.openNested(Transaction.getCurrentUnsafe());
		return null;
	}
}
