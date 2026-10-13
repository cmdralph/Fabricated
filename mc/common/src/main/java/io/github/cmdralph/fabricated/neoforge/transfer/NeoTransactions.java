package io.github.cmdralph.fabricated.neoforge.transfer;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Mirrors Fabric transactions as NeoForge ones, for NeoForge handlers used by Fabric code. A NeoForge transaction is
 * opened for the Fabric transaction (and each of its parents, so an outer abort still reverts) the first time a
 * handler is used in it, and closed the same way when that Fabric transaction closes.
 */
final class NeoTransactions {
	private static final ThreadLocal<NeoTransactions> STATE = ThreadLocal.withInitial(NeoTransactions::new);

	/** Index = Fabric nesting depth. */
	private final List<net.fabricmc.fabric.api.transfer.v1.transaction.Transaction> fabric = new ArrayList<>();
	private final List<Transaction> neo = new ArrayList<>();

	private NeoTransactions() {
	}

	/** The NeoForge transaction standing for {@code transaction}, opened on first use. */
	@SuppressWarnings("deprecation") // The current NeoForge transaction can't be passed through Fabric's API.
	static net.neoforged.neoforge.transfer.transaction.TransactionContext of(TransactionContext transaction) {
		NeoTransactions state = STATE.get();
		int depth = transaction.nestingDepth();
		for (int i = 0; i <= depth; i++) {
			net.fabricmc.fabric.api.transfer.v1.transaction.Transaction outer = transaction.getOpenTransaction(i);
			if (i < state.fabric.size() && state.fabric.get(i) == outer) continue;
			Transaction mirror = Transaction.open(Transaction.getCurrentOpenedTransaction());
			state.fabric.add(outer);
			state.neo.add(mirror);
			int index = i;
			outer.addCloseCallback((closed, result) -> {
				state.fabric.subList(index, state.fabric.size()).clear();
				state.neo.subList(index, state.neo.size()).clear();
				if (result.wasCommitted()) {
					mirror.commit();
				} else {
					mirror.close();
				}
			});
		}
		return state.neo.get(depth);
	}
}
