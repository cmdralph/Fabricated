package io.github.cmdralph.fabricated.neoforge.transfer;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Mirrors Fabric transactions as NeoForge ones, for NeoForge handlers used by Fabric code. A NeoForge transaction is
 * opened for the Fabric transaction (and each of its parents, so an outer abort still reverts) the first time a
 * handler is used in it, and closed the same way when that Fabric transaction closes.
 *
 * <p>A Fabric transaction that {@link FabricTransactions} opened for a NeoForge one (NeoForge code using a Fabric
 * storage, which uses a NeoForge handler in turn) gets no mirror: the NeoForge side is already in that transaction.
 */
final class NeoTransactions {
	private static final ThreadLocal<NeoTransactions> STATE = ThreadLocal.withInitial(NeoTransactions::new);

	/** A NeoForge transaction opened for a Fabric one. */
	record Mirror(net.fabricmc.fabric.api.transfer.v1.transaction.Transaction fabric, Transaction neo) {
	}

	/** Index = Fabric nesting depth; no mirror where the Fabric transaction stands for a NeoForge one. */
	private final List<net.fabricmc.fabric.api.transfer.v1.transaction.Transaction> fabric = new ArrayList<>();
	private final List<@Nullable Transaction> neo = new ArrayList<>();

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
			Transaction mirror = FabricTransactions.owns(outer) ? null : Transaction.open(Transaction.getCurrentOpenedTransaction());
			state.fabric.add(outer);
			state.neo.add(mirror);
			int index = i;
			outer.addCloseCallback((closed, result) -> {
				state.fabric.subList(index, state.fabric.size()).clear();
				state.neo.subList(index, state.neo.size()).clear();
				if (mirror == null) return;
				if (result.wasCommitted()) {
					mirror.commit();
				} else {
					mirror.close();
				}
			});
		}
		Transaction mirror = state.neo.get(depth);
		// Without one, the NeoForge transaction it stands for is the innermost open one.
		return mirror != null ? mirror : Transaction.getCurrentOpenedTransaction();
	}

	/** This thread's open mirrors, outermost first. */
	static List<Mirror> mirrors() {
		NeoTransactions state = STATE.get();
		if (state.neo.isEmpty()) return List.of(); // the usual case: NeoForge code using a Fabric storage directly
		List<Mirror> mirrors = new ArrayList<>(state.neo.size());
		for (int i = 0; i < state.neo.size(); i++) {
			Transaction mirror = state.neo.get(i);
			if (mirror != null) mirrors.add(new Mirror(state.fabric.get(i), mirror));
		}
		return mirrors;
	}
}
