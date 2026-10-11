package net.fabricmc.fabric.impl.networking.client;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;

/**
 * Client login-phase receivers. Fabricated does not run Fabric login queries on NeoForge (see
 * {@code ServerLoginNetworkAddon}), so these receivers are kept for API compatibility but never called.
 */
public final class ClientLoginNetworkAddon {
	private final Map<Identifier, ClientLoginNetworking.LoginQueryRequestHandler> receivers = new ConcurrentHashMap<>();

	public boolean registerChannel(Identifier channelName, ClientLoginNetworking.LoginQueryRequestHandler handler) {
		Objects.requireNonNull(channelName, "Channel name cannot be null");
		Objects.requireNonNull(handler, "Query handler cannot be null");
		return this.receivers.putIfAbsent(channelName, handler) == null;
	}

	public ClientLoginNetworking.@Nullable LoginQueryRequestHandler unregisterChannel(Identifier channelName) {
		return this.receivers.remove(channelName);
	}
}
