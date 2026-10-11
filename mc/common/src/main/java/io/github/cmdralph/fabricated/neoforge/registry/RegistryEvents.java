package io.github.cmdralph.fabricated.neoforge.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.fabricmc.fabric.api.event.registry.RegistryIdRemapCallback;
import net.fabricmc.fabric.impl.registry.sync.ListenableRegistry;
import net.fabricmc.fabric.impl.registry.sync.RemapStateImpl;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.callback.AddCallback;
import net.neoforged.neoforge.registries.callback.BakeCallback;
import net.neoforged.neoforge.registries.callback.ClearCallback;

/**
 * Fabric's entry-added and id-remap events for one registry, fired from NeoForge's registry callbacks:
 * <ul>
 *   <li>{@code RegistryEntryAddedCallback}: NeoForge's add callback, except while NeoForge restores a registry
 *       from a snapshot (those entries were already announced).</li>
 *   <li>{@code RegistryIdRemapCallback}: when NeoForge applies a snapshot (for example the server's ids on joining
 *       it), the ids before the clear are compared with the ids after the registry is frozen again; the event fires
 *       when any changed.</li>
 * </ul>
 * The callbacks are attached the first time Fabric code asks for a registry's events.
 */
public final class RegistryEvents<T> implements ListenableRegistry<T> {
	private static final Map<Registry<?>, RegistryEvents<?>> EVENTS = new ConcurrentHashMap<>();

	private final Registry<T> registry;
	private final Event<RegistryEntryAddedCallback<T>> addEvent;
	private final Event<RegistryIdRemapCallback<T>> remapEvent;
	private @Nullable Int2ObjectMap<Identifier> idsBeforeClear;
	private boolean restoring;

	private RegistryEvents(Registry<T> registry) {
		this.registry = registry;
		this.addEvent = EventFactory.createArrayBacked(RegistryEntryAddedCallback.class, callbacks -> (rawId, id, object) -> {
			for (RegistryEntryAddedCallback<T> callback : callbacks) callback.onEntryAdded(rawId, id, object);
		});
		this.remapEvent = EventFactory.createArrayBacked(RegistryIdRemapCallback.class, callbacks -> state -> {
			for (RegistryIdRemapCallback<T> callback : callbacks) callback.onRemap(state);
		});
		registry.addCallback((AddCallback<T>) this::onAdd);
		registry.addCallback((ClearCallback<T>) this::onClear);
		registry.addCallback((BakeCallback<T>) this::onBake);
	}

	@SuppressWarnings("unchecked")
	public static <T> RegistryEvents<T> of(Registry<T> registry) {
		return (RegistryEvents<T>) EVENTS.computeIfAbsent(registry, r -> new RegistryEvents<>((Registry<T>) r));
	}

	@Override
	public Event<RegistryEntryAddedCallback<T>> fabric_getAddObjectEvent() {
		return this.addEvent;
	}

	@Override
	public Event<RegistryIdRemapCallback<T>> fabric_getRemapEvent() {
		return this.remapEvent;
	}

	private void onAdd(Registry<T> registry, int id, ResourceKey<T> key, T value) {
		if (!this.restoring) this.addEvent.invoker().onEntryAdded(id, key.identifier(), value);
	}

	private synchronized void onClear(Registry<T> registry, boolean full) {
		if (this.idsBeforeClear == null) {
			Int2ObjectMap<Identifier> ids = new Int2ObjectOpenHashMap<>();
			for (T value : registry) {
				Identifier id = registry.getKey(value);
				if (id != null) ids.put(registry.getId(value), id);
			}
			this.idsBeforeClear = ids;
		}
		this.restoring = full;
	}

	private void onBake(Registry<T> registry) {
		Int2ObjectMap<Identifier> before;
		synchronized (this) {
			before = this.idsBeforeClear;
			this.idsBeforeClear = null;
			this.restoring = false;
		}
		if (before == null) return;
		Int2IntMap changed = new Int2IntOpenHashMap();
		for (Int2ObjectMap.Entry<Identifier> entry : before.int2ObjectEntrySet()) {
			if (!registry.containsKey(entry.getValue())) continue;
			int now = registry.getId(entry.getValue());
			if (now != entry.getIntKey()) changed.put(entry.getIntKey(), now);
		}
		if (!changed.isEmpty()) this.remapEvent.invoker().onRemap(new RemapStateImpl<>(this.registry, before, changed));
	}
}
