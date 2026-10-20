package io.github.cmdralph.fabricated.neoforge.transfer;

import java.util.ArrayDeque;

import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Mirrors NeoForge transactions as Fabric ones, for Fabric storages used by NeoForge code. Each NeoForge depth that
 * touches a Fabric storage gets one Fabric transaction, nested in the innermost open one; NeoForge's journal then
 * aborts it when its transaction aborts and commits it when its transaction commits (for the root: once it is
 * applied). One journal per thread, shared by every bridged storage, as both APIs confine transactions to a thread.
 *
 * <p>A NeoForge transaction that itself mirrors a Fabric one ({@link NeoTransactions}: Fabric code using a NeoForge
 * handler, which uses a Fabric storage in turn, as an item's handler uses its container) stands for that Fabric
 * transaction instead of getting one of its own: it is Fabric code's to close, before the NeoForge one closes.
 */
final class FabricTransactions extends SnapshotJournal<Object> {
	private static final ThreadLocal<FabricTransactions> JOURNAL = ThreadLocal.withInitial(FabricTransactions::new);
	/** The snapshot of a NeoForge depth that mirrors a Fabric transaction: nothing of this journal's to close. */
	private static final Object BORROWED = new Object();

	/** This journal's open Fabric transactions, innermost first; they open and close in stack order. */
	private final ArrayDeque<Transaction> open = new ArrayDeque<>();
	/** Whether the snapshot being taken is of a mirroring NeoForge transaction. */
	private boolean borrowing;

	private FabricTransactions() {
	}

	/** The Fabric transaction standing for {@code transaction}, opened on first use. */
	static Transaction of(TransactionContext transaction) {
		FabricTransactions journal = JOURNAL.get();
		// The mirroring depths get their (borrowed) snapshots first, so a Fabric transaction this journal opens deeper
		// commits into its Fabric parent with its NeoForge transaction, rather than moving up to outlive it.
		Transaction mirrored = null;
		for (NeoTransactions.Mirror mirror : NeoTransactions.mirrors()) {
			if (mirror.neo().depth() > transaction.depth()) break;
			journal.borrowing = true;
			try {
				journal.updateSnapshots(mirror.neo());
			} finally {
				journal.borrowing = false;
			}
			if (mirror.neo() == transaction) mirrored = mirror.fabric();
		}
		if (mirrored != null) return mirrored;
		journal.updateSnapshots(transaction);
		return journal.open.peek();
	}

	/** Whether {@code transaction} is one this thread's journal opened for a NeoForge transaction, still open. */
	static boolean owns(Transaction transaction) {
		return JOURNAL.get().open.contains(transaction);
	}

	@Override
	@SuppressWarnings("deprecation") // The current Fabric transaction can't be passed through NeoForge's API.
	protected Object createSnapshot() {
		if (borrowing) return BORROWED;
		Transaction transaction = Transaction.openNested(Transaction.getCurrentUnsafe());
		open.push(transaction);
		return transaction;
	}

	@Override
	protected void revertToSnapshot(Object snapshot) {
		if (snapshot instanceof Transaction transaction) close(transaction, false);
	}

	/** Called after a revert (already closed), or when a nested commit is folded into an older snapshot: commit it. */
	@Override
	protected void releaseSnapshot(Object snapshot) {
		if (snapshot instanceof Transaction transaction && open.peek() == transaction) close(transaction, true);
	}

	@Override
	protected void onRootCommit(Object originalState) {
		if (originalState instanceof Transaction transaction) close(transaction, true);
	}

	private void close(Transaction transaction, boolean commit) {
		open.pop();
		if (commit) {
			transaction.commit();
		} else {
			transaction.abort();
		}
	}
}
