package net.fabricmc.fabric.impl.networking.client;

import org.jspecify.annotations.Nullable;

import io.github.cmdralph.fabricated.neoforge.networking.NetworkAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.impl.networking.GlobalReceiverRegistry;

/** Fabricated implementation of Fabric's client-side networking state, driven by NeoForge's payload system. */
public final class ClientNetworkingImpl {
	public static final GlobalReceiverRegistry<ClientLoginNetworking.LoginQueryRequestHandler> LOGIN = new GlobalReceiverRegistry<>();
	public static final GlobalReceiverRegistry<ClientConfigurationNetworking.ConfigurationPayloadHandler<?>> CONFIGURATION = new GlobalReceiverRegistry<>();
	public static final GlobalReceiverRegistry<ClientPlayNetworking.PlayPayloadHandler<?>> PLAY = new GlobalReceiverRegistry<>();

	private static volatile @Nullable ClientConfigurationPacketListenerImpl currentConfiguration;

	private ClientNetworkingImpl() {
	}

	public static ClientPlayNetworkAddon getAddon(ClientPacketListener listener) {
		return NetworkAddons.get(listener, ClientPlayNetworkAddon.class, ClientPlayNetworkAddon::new);
	}

	public static ClientConfigurationNetworkAddon getAddon(ClientConfigurationPacketListenerImpl listener) {
		return NetworkAddons.get(listener, ClientConfigurationNetworkAddon.class, ClientConfigurationNetworkAddon::new);
	}

	public static ClientLoginNetworkAddon getAddon(ClientHandshakePacketListenerImpl listener) {
		return NetworkAddons.get(listener, ClientLoginNetworkAddon.class, l -> new ClientLoginNetworkAddon());
	}

	public static @Nullable ClientPlayNetworkAddon getClientPlayAddon() {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		return connection != null ? getAddon(connection) : null;
	}

	public static @Nullable ClientConfigurationNetworkAddon getClientConfigurationAddon() {
		ClientConfigurationPacketListenerImpl listener = currentConfiguration;
		return listener != null ? getAddon(listener) : null;
	}

	/** Fabric login queries are not supported (see {@link ClientLoginNetworkAddon}); there is never a login connection. */
	public static @Nullable Connection getLoginConnection() {
		return null;
	}

	public static Packet<ServerCommonPacketListener> createServerboundPacket(CustomPacketPayload payload) {
		return new ServerboundCustomPayloadPacket(payload);
	}

	/** Called by Fabricated when a configuration phase starts and ends on the client. */
	public static void setCurrentConfiguration(@Nullable ClientConfigurationPacketListenerImpl listener) {
		currentConfiguration = listener;
	}

	public static @Nullable ClientConfigurationPacketListenerImpl currentConfiguration() {
		return currentConfiguration;
	}
}
