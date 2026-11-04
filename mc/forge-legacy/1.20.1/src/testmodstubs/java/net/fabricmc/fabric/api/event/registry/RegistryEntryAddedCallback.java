package net.fabricmc.fabric.api.event.registry;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.class_2378;
import net.minecraft.class_2960;

/**
 * Stand-in for fabric-registry-sync-v0's {@code RegistryEntryAddedCallback}, which the forge-legacy test mod uses
 * (signature as upstream's 0.92.12 for 1.20.1, in Intermediary's names). Never packaged.
 */
@FunctionalInterface
public interface RegistryEntryAddedCallback<T> {
	void onEntryAdded(int rawId, class_2960 id, T object);

	static <T> Event<RegistryEntryAddedCallback<T>> event(class_2378<T> registry) {
		throw new AssertionError("stand-in");
	}
}
