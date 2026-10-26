package net.minecraft;

/**
 * {@code Registry}: {@code method_10230} is {@code register(Registry, ResourceLocation, T)}, {@code method_10221}
 * {@code getKey(T)}, {@code method_10223} {@code get(ResourceLocation)}, {@code method_30517} {@code key()},
 * {@code method_40266} {@code getTag(TagKey)}.
 */
public interface class_2378<T> {
	static <V, E extends V> E method_10230(class_2378<V> registry, class_2960 id, E entry) {
		throw new AssertionError("stand-in");
	}

	class_2960 method_10221(T entry);

	T method_10223(class_2960 id);

	class_5321<? extends class_2378<T>> method_30517();

	java.util.Optional<?> method_40266(class_6862<T> tag);
}
