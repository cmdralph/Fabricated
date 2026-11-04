package net.fabricmc.fabric.api.event.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.event.Event;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 interface that the in-game mod and the forge test mod call,
 * in Mojang's names.
 */
@FunctionalInterface
public interface RegistryEntryAddedCallback<T> {
	void onEntryAdded(int rawId, Identifier id, T object);

	static <T> Event<RegistryEntryAddedCallback<T>> event(Registry<T> registry) {
		throw new AssertionError("stand-in");
	}
}
