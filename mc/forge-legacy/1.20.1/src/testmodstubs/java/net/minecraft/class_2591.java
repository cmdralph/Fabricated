package net.minecraft;

import com.mojang.datafixers.types.Type;

/**
 * {@code BlockEntityType}; {@code class_2592} is its {@code Builder}, whose {@code method_20528} is {@code of} and
 * {@code method_11034} {@code build}, and {@code class_5559} its {@code BlockEntitySupplier}.
 */
public final class class_2591<T extends class_2586> {
	private class_2591() {
	}

	public static final class class_2592<T extends class_2586> {
		private class_2592() {
		}

		public static <T extends class_2586> class_2592<T> method_20528(class_5559<? extends T> factory, class_2248... blocks) {
			throw new AssertionError("stand-in");
		}

		public class_2591<T> method_11034(Type<?> type) {
			throw new AssertionError("stand-in");
		}
	}

	@FunctionalInterface
	public interface class_5559<T extends class_2586> {
		T create(class_2338 pos, class_2680 state);
	}
}
