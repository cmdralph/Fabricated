package net.minecraft;

/** {@code Holder}: {@code class_6883} is {@code Holder.Reference}, {@code comp_349} {@code value()}. */
public interface class_6880<T> {
	T comp_349();

	/** {@code Holder.Reference}. */
	final class class_6883<T> implements class_6880<T> {
		private class_6883() {
		}

		@Override
		public T comp_349() {
			throw new AssertionError("stand-in");
		}
	}
}
