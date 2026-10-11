package io.github.cmdralph.fabricated.neoforge.resource;

import java.util.function.Consumer;

import net.minecraft.server.packs.PackType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

/** Client half of {@link ResourceLoaderBridge}: Fabric client resource listeners join NeoForge's client event. */
final class ClientResourceLoaderBridge {
	private ClientResourceLoaderBridge() {
	}

	static void register(IEventBus modBus, Consumer<String> problems) {
		modBus.addListener(EventPriority.LOWEST, AddClientReloadListenersEvent.class, event -> {
			ResourceRegistry registry = ResourceRegistry.of(PackType.CLIENT_RESOURCES);
			ReloadListeners.apply(event, null, registry.listeners(), registry.orderings(), ClientReloadAliases.ALIASES, problems);
		});
	}
}
