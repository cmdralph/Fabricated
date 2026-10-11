package net.fabricmc.fabric.impl.networking.server;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.GlobalReceiverRegistry;

/** Fabricated implementation of Fabric's server-side networking state, driven by NeoForge's payload system. */
public final class ServerNetworkingImpl {
	public static final GlobalReceiverRegistry<ServerLoginNetworking.LoginQueryResponseHandler> LOGIN = new GlobalReceiverRegistry<>();
	public static final GlobalReceiverRegistry<ServerConfigurationNetworking.ConfigurationPacketHandler<?>> CONFIGURATION = new GlobalReceiverRegistry<>();
	public static final GlobalReceiverRegistry<ServerPlayNetworking.PlayPayloadHandler<?>> PLAY = new GlobalReceiverRegistry<>();

	// Addons live as long as their packet listener; weak keys let finished connections be collected.
	private static final Map<ServerGamePacketListenerImpl, ServerPlayNetworkAddon> PLAY_ADDONS = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<ServerConfigurationPacketListenerImpl, ServerConfigurationNetworkAddon> CONFIGURATION_ADDONS = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<ServerLoginPacketListenerImpl, ServerLoginNetworkAddon> LOGIN_ADDONS = Collections.synchronizedMap(new WeakHashMap<>());
	/** Connections sent back to the configuration phase by {@code ServerPlayNetworking.reconfigure}. */
	static final Set<Connection> RECONFIGURING = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	private ServerNetworkingImpl() {
	}

	public static ServerPlayNetworkAddon getAddon(ServerGamePacketListenerImpl listener) {
		return PLAY_ADDONS.computeIfAbsent(listener, ServerPlayNetworkAddon::new);
	}

	public static ServerConfigurationNetworkAddon getAddon(ServerConfigurationPacketListenerImpl listener) {
		return CONFIGURATION_ADDONS.computeIfAbsent(listener, ServerConfigurationNetworkAddon::new);
	}

	public static ServerLoginNetworkAddon getAddon(ServerLoginPacketListenerImpl listener) {
		return LOGIN_ADDONS.computeIfAbsent(listener, ServerLoginNetworkAddon::new);
	}

	public static Packet<ClientCommonPacketListener> createS2CPacket(CustomPacketPayload payload) {
		return new ClientboundCustomPayloadPacket(payload);
	}
}
