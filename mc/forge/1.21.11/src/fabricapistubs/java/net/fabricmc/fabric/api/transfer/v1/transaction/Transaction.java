package net.fabricmc.fabric.api.transfer.v1.transaction;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod calls, in Mojang's
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
