package team.reborn.energy.api;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/**
 * Compile-time stand-in for Team Reborn's energy storage (the real one is the Fabric library's, loaded at runtime when a
 * Fabric mod brings it); never packaged. Only the members Fabricated's bridge uses, with the library's signatures.
 */
public interface EnergyStorage {
	default boolean supportsInsertion() {
		return true;
	}

	long insert(long maxAmount, TransactionContext transaction);

	default boolean supportsExtraction() {
		return true;
	}

	long extract(long maxAmount, TransactionContext transaction);

	long getAmount();

	long getCapacity();
}
