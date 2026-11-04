package net.minecraft;

/**
 * {@code Registry}: {@code method_10230} is {@code register(Registry, ResourceLocation, T)}, {@code method_10221}
 * {@code getKey(T)}, {@code method_10223} {@code get(ResourceLocation)}, {@code method_30517} {@code key()},
 * {@code method_40266} {@code getTag(TagKey)}, {@code method_31140} {@code getOrThrow(ResourceKey)}, {@code method_40269}
 * {@code createIntrusiveHolder(T)}, {@code method_10250} {@code containsKey(ResourceLocation)}, {@code method_40264}
 * {@code getHolder(ResourceKey)}, {@code method_10206} {@code getId(T)} (its raw id; {@code IdMap}'s).
 */
public interface class_2378<T> {
	static <V, E extends V> E method_10230(class_2378<V> registry, class_2960 id, E entry) {
		throw new AssertionError("stand-in");
	}

	class_2960 method_10221(T entry);

	T method_10223(class_2960 id);

	class_5321<? extends class_2378<T>> method_30517();

	java.util.Optional<?> method_40266(class_6862<T> tag);

	T method_31140(class_5321<T> key);

	class_6880.class_6883<T> method_40269(T value);

	boolean method_10250(class_2960 id);

	java.util.Optional<class_6880.class_6883<T>> method_40264(class_5321<T> key);

	int method_10206(T entry);
}
