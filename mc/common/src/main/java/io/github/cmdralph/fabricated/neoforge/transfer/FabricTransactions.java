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
 */
final class FabricTransactions extends SnapshotJournal<Transaction> {
	private static final ThreadLocal<FabricTransactions> JOURNAL = ThreadLocal.withInitial(FabricTransactions::new);

	/** This journal's open Fabric transactions, innermost first; they open and close in stack order. */
	private final ArrayDeque<Transaction> open = new ArrayDeque<>();

	private FabricTransactions() {
	}

	/** The Fabric transaction standing for {@code transaction}, opened on first use. */
	static Transaction of(TransactionContext transaction) {
		FabricTransactions journal = JOURNAL.get();
		journal.updateSnapshots(transaction);
		return journal.open.peek();
	}

	@Override
	@SuppressWarnings("deprecation") // The current Fabric transaction can't be passed through NeoForge's API.
	protected Transaction createSnapshot() {
		Transaction transaction = Transaction.openNested(Transaction.getCurrentUnsafe());
		open.push(transaction);
		return transaction;
	}

	@Override
	protected void revertToSnapshot(Transaction snapshot) {
		close(snapshot, false);
	}

	/** Called after a revert (already closed), or when a nested commit is folded into an older snapshot: commit it. */
	@Override
	protected void releaseSnapshot(Transaction snapshot) {
		if (open.peek() == snapshot) close(snapshot, true);
	}

	@Override
	protected void onRootCommit(Transaction originalState) {
		close(originalState, true);
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
