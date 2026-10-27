package net.minecraftforge.common.util;

/** {@code NonNullSupplier}. */
@FunctionalInterface
public interface NonNullSupplier<T> {
	T get();
}
