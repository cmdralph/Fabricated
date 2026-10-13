package net.fabricmc.fabric.impl.networking.server;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import io.github.cmdralph.fabricated.neoforge.networking.NetworkAddons;
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

	/** Connections sent back to the configuration phase by {@code ServerPlayNetworking.reconfigure}. */
	static final Set<Connection> RECONFIGURING = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	private ServerNetworkingImpl() {
	}

	public static ServerPlayNetworkAddon getAddon(ServerGamePacketListenerImpl listener) {
		return NetworkAddons.get(listener, ServerPlayNetworkAddon.class, ServerPlayNetworkAddon::new);
	}

	public static ServerConfigurationNetworkAddon getAddon(ServerConfigurationPacketListenerImpl listener) {
		return NetworkAddons.get(listener, ServerConfigurationNetworkAddon.class, ServerConfigurationNetworkAddon::new);
	}

	public static ServerLoginNetworkAddon getAddon(ServerLoginPacketListenerImpl listener) {
		return NetworkAddons.get(listener, ServerLoginNetworkAddon.class, ServerLoginNetworkAddon::new);
	}

	public static Packet<ClientCommonPacketListener> createS2CPacket(CustomPacketPayload payload) {
		return new ClientboundCustomPayloadPacket(payload);
	}
}
