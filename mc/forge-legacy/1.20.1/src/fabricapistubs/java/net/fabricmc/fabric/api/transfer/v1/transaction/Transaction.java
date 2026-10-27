package net.fabricmc.fabric.api.transfer.v1.transaction;

/**
 * Compile-time stand-in: the members of Fabric API's 1.20.1 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public interface Transaction extends AutoCloseable, TransactionContext {
	static Transaction openOuter() {
		throw new AssertionError("stand-in");
	}

	static Lifecycle getLifecycle() {
		throw new AssertionError("stand-in");
	}

	static TransactionContext getCurrentUnsafe() {
		throw new AssertionError("stand-in");
	}

	static Transaction openNested(TransactionContext outer) {
		throw new AssertionError("stand-in");
	}

	/** Upstream declares it in {@code TransactionContext}, which the stand-ins name only. */
	Transaction openNested();

	void abort();

	void commit();

	@Override
	void close();

	enum Lifecycle {
		NONE,
		OPEN,
		CLOSING,
		OUTER_CLOSING
	}
}
