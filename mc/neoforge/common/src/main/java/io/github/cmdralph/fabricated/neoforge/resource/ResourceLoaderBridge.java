package io.github.cmdralph.fabricated.neoforge.resource;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.fabricmc.fabric.impl.resource.FabricDataResourceStoreHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/**
 * Native {@code fabric-resource-loader-v1}: Fabric reload listeners join NeoForge's
 * {@code AddServerReloadListenersEvent} / {@code AddClientReloadListenersEvent}, and built-in packs are offered
 * through {@code AddPackFindersEvent}. Mod resources themselves need nothing: NeoForge already loads the
 * {@code assets/} and {@code data/} of every mod, Fabric mods included.
 *
 * <p>Runs at the lowest priority, so the listeners NeoForge mods add are known when Fabric orderings refer to them.
 */
public final class ResourceLoaderBridge {
	/** Fabric's vanilla server listener ids to NeoForge's (snake_case of the listener's class). */
	private static final Map<Identifier, Identifier> SERVER_ALIASES = Map.of(
			ReloadListeners.vanilla("recipes"), ReloadListeners.vanilla("recipe_manager"),
			ReloadListeners.vanilla("functions"), ReloadListeners.vanilla("server_function_library"),
			ReloadListeners.vanilla("advancements"), ReloadListeners.vanilla("server_advancement_manager"));

	private ResourceLoaderBridge() {
	}

	/** @param problems receives one line per problem worth a place in the report */
	public static void register(IEventBus modBus, Dist dist, Consumer<String> problems) {
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, AddServerReloadListenersEvent.class, e -> onServerReload(e, problems));
		modBus.addListener(AddPackFindersEvent.class, BuiltinPacks::onAddPackFinders);
		if (dist.isClient()) ClientResourceLoaderBridge.register(modBus, problems);
	}

	private static void onServerReload(AddServerReloadListenersEvent event, Consumer<String> problems) {
		ReloadableServerResources resources = event.getServerResources();
		HolderLookup.Provider registries = resources.getRegistryLookup();
		FeatureFlagSet features = resources.getConditionContext().enabledFeatures();
		DataResourceStore.Mutable store = ((FabricDataResourceStoreHolder) resources).fabric$getDataResourceStore();

		ResourceRegistry registry = ResourceRegistry.of(PackType.SERVER_DATA);
		Map<Identifier, PreparableReloadListener> listeners = registry.listeners();
		for (Map.Entry<Identifier, Function<HolderLookup.Provider, PreparableReloadListener>> entry : registry.factories().entrySet()) {
			// A factory is mod code: if it throws, this reload fails, as it would on Fabric. Say whose it was.
			PreparableReloadListener listener;
			try {
				listener = entry.getValue().apply(registries);
			} catch (RuntimeException e) {
				throw new IllegalStateException("Fabric reload listener factory " + entry.getKey() + " failed", e);
			}
			if (listener != null) listeners.put(entry.getKey(), listener);
		}

		ReloadListeners.apply(event, new ServerSetup(resources, registries, features, store), listeners, registry.orderings(), SERVER_ALIASES, problems);
	}

	/** Provides Fabric's shared reload state ({@code DataResourceLoader}'s keys) before any listener runs. */
	private record ServerSetup(ReloadableServerResources resources, HolderLookup.Provider registries, FeatureFlagSet features,
			DataResourceStore.Mutable store) implements ResourceManagerReloadListener {
		@Override
		public void prepareSharedState(SharedState state) {
			ServerReloadState.prepare(state, this.resources, this.registries, this.features, this.store);
		}

		@Override
		public void onResourceManagerReload(ResourceManager manager) {
		}
	}
}
