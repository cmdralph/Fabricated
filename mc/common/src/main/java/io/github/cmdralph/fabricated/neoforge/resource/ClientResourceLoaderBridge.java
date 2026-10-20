package io.github.cmdralph.fabricated.neoforge.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;

/** Client half of {@link ResourceLoaderBridge}: Fabric client resource listeners join NeoForge's client event. */
public final class ClientResourceLoaderBridge {
	/** Listeners registered the vanilla way before NeoForge's event, in order; the event adds them. */
	private static final List<PreparableReloadListener> EARLY = new ArrayList<>();
	private static boolean eventFired;

	private ClientResourceLoaderBridge() {
	}

	static void register(IEventBus modBus, Consumer<String> problems) {
		modBus.addListener(EventPriority.LOWEST, AddClientReloadListenersEvent.class, event -> {
			ResourceRegistry registry = ResourceRegistry.of(PackType.CLIENT_RESOURCES);
			ReloadListeners.apply(event, null, registry.listeners(), registry.orderings(), ClientReloadAliases.ALIASES, problems);
			addEarlyListeners(event);
		});
	}

	/**
	 * Whether to hold back {@code listener}, registered the vanilla way on the client's resource manager (see
	 * {@code ClientReloadListenerMixin}): before NeoForge's {@code AddClientReloadListenersEvent}, which refuses any
	 * listener it can't name, a mod's listener (registered from its mixin into {@code Minecraft}'s constructor, as on
	 * Fabric) is added through that event instead, after vanilla's.
	 */
	public static synchronized boolean holdBack(ReloadableResourceManager manager, PreparableReloadListener listener) {
		if (eventFired || VanillaClientListeners.getNameForClass(listener.getClass()) != null) return false;
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getResourceManager() != manager) return false;
		EARLY.add(listener);
		return true;
	}

	private static synchronized void addEarlyListeners(AddClientReloadListenersEvent event) {
		eventFired = true;
		for (int i = 0; i < EARLY.size(); i++) event.addListener(Identifier.fromNamespaceAndPath("fabricated", "early_listener_" + i), EARLY.get(i));
		EARLY.clear();
	}
}
