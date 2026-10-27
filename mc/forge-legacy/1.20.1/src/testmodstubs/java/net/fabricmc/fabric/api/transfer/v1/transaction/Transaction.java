package net.fabricmc.fabric.api.transfer.v1.transaction;

public interface Transaction extends AutoCloseable, TransactionContext {
	static Transaction openOuter() {
		throw new AssertionError("stand-in");
	}

	void commit();

	@Override
	void close();
}
