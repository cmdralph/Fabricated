package net.fabricmc.fabric.api.event.registry;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

/**
 * Compile-time stand-in: the members of Fabric API's 1.20.1 interface that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
@FunctionalInterface
public interface RegistryEntryAddedCallback<T> {
	void onEntryAdded(int rawId, ResourceLocation id, T object);

	static <T> Event<RegistryEntryAddedCallback<T>> event(Registry<T> registry) {
		throw new AssertionError("stand-in");
	}
}
