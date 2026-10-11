package net.fabricmc.fabric.impl.registry.sync;

import io.github.cmdralph.fabricated.neoforge.registry.RegistryEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.fabricmc.fabric.api.event.registry.RegistryIdRemapCallback;
import net.minecraft.core.Registry;

/**
 * Fabricated implementation of Fabric's per-registry events. Upstream mixes this interface into every registry;
 * Fabricated keeps one {@link RegistryEvents} per registry instead, fed by NeoForge's registry callbacks.
 */
public interface ListenableRegistry<T> {
	Event<RegistryEntryAddedCallback<T>> fabric_getAddObjectEvent();

	Event<RegistryIdRemapCallback<T>> fabric_getRemapEvent();

	static <T> ListenableRegistry<T> get(Registry<T> registry) {
		return RegistryEvents.of(registry);
	}
}
