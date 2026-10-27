package net.minecraftforge.common.util;

import java.util.Optional;

/** {@code LazyOptional}. */
public final class LazyOptional<T> {
	private LazyOptional() {
	}

	public static <T> LazyOptional<T> of(NonNullSupplier<T> supplier) {
		throw new AssertionError("stand-in");
	}

	public static <T> LazyOptional<T> empty() {
		throw new AssertionError("stand-in");
	}

	public boolean isPresent() {
		throw new AssertionError("stand-in");
	}

	public <X> LazyOptional<X> cast() {
		throw new AssertionError("stand-in");
	}

	public Optional<T> resolve() {
		throw new AssertionError("stand-in");
	}
}
