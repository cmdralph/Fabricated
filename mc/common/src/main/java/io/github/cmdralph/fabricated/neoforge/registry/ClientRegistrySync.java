package io.github.cmdralph.fabricated.neoforge.registry;

import java.util.function.Consumer;

import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client half of {@link DynamicRegistrySetup}: the registries a server sent, once the player joins it. */
final class ClientRegistrySync {
	private ClientRegistrySync() {
	}

	static void register(Consumer<String> problems) {
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> {
			LocalPlayer player = e.getPlayer();
			DynamicRegistrySetup.fire(player.connection.registryAccess(), "client", problems);
		});
	}
}
