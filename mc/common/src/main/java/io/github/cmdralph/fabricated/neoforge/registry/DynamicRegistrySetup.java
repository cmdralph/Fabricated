package io.github.cmdralph.fabricated.neoforge.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.event.registry.DynamicRegistrySetupCallback;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.fabricmc.fabric.impl.registry.sync.DynamicRegistryViewImpl;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;

/**
 * Fires Fabric's {@code DynamicRegistrySetupCallback}. Upstream fires it just before the dynamic (data-pack)
 * registries are filled; NeoForge has no hook there, and Fabricated doesn't patch Minecraft's loading code. So it
 * fires once they are loaded (server: before the server starts; client: on joining a server), and every
 * entry-added callback registered through the view is called once for each entry already present, in id order.
 * The difference: entries can no longer be added from the callback (the registries are frozen by then).
 */
public final class DynamicRegistrySetup {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	private DynamicRegistrySetup() {
	}

	/** A callback a mod registered on the view, to be replayed for the registry's existing entries. */
	public record Recorded<T>(Registry<T> registry, RegistryEntryAddedCallback<T> callback) {
		void replay() {
			for (Holder.Reference<T> holder : this.registry.listElements().toList()) {
				this.callback.onEntryAdded(this.registry.getId(holder.value()), holder.key().identifier(), holder.value());
			}
		}
	}

	static void fire(RegistryAccess access, String side, Consumer<String> problems) {
		Map<ResourceKey<? extends Registry<?>>, Registry<?>> registries = new LinkedHashMap<>();
		access.registries().forEach(entry -> {
			// Static registries are covered by RegistryEntryAddedCallback; the view is about the dynamic ones.
			if (!BuiltInRegistries.REGISTRY.containsKey(entry.key().identifier())) registries.put(entry.key(), entry.value());
		});
		List<Recorded<?>> recorded = new ArrayList<>();
		try {
			DynamicRegistrySetupCallback.EVENT.invoker().onRegistrySetup(new DynamicRegistryViewImpl(registries, recorded::add));
			for (Recorded<?> callback : recorded) callback.replay();
		} catch (RuntimeException e) {
			// Mod code: report whose callback failed instead of hiding it, but don't stop the world from loading.
			String problem = "A Fabric DynamicRegistrySetupCallback failed on the " + side + ": " + e;
			LOGGER.error(problem, e);
			problems.accept(problem);
		}
	}
}
