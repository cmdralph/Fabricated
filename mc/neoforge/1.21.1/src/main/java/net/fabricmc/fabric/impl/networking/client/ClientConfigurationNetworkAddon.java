package net.fabricmc.fabric.impl.networking.client;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.impl.networking.ChanneledNetworkAddon;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;

/** Client configuration-phase networking state. */
public final class ClientConfigurationNetworkAddon extends ChanneledNetworkAddon<ClientConfigurationNetworking.ConfigurationPayloadHandler<?>> {
	private final ClientConfigurationPacketListenerImpl listener;

	ClientConfigurationNetworkAddon(ClientConfigurationPacketListenerImpl listener) {
		super(ClientNetworkingImpl.CONFIGURATION, listener.getConnection());
		this.listener = listener;
	}

	@Override
	public Set<ResourceLocation> getSendableChannels() {
		Set<ResourceLocation> channels = new HashSet<>();
		for (ResourceLocation id : PayloadTypeRegistryImpl.CONFIGURATION_C2S.ids()) {
			if (this.listener.hasChannel(id)) channels.add(id);
		}
		return channels;
	}

	@Override
	public Packet<?> createPacket(CustomPacketPayload payload) {
		return ClientNetworkingImpl.createC2SPacket(payload);
	}

	/** Dispatches a payload NeoForge decoded on the network thread. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void handle(CustomPacketPayload payload) {
		ClientConfigurationNetworking.ConfigurationPayloadHandler handler = getHandler(payload.type().id());
		if (handler == null) return;
		Context context = new Context(Minecraft.getInstance(), this.listener, this);
		handler.receive(payload, context);
	}

	private record Context(Minecraft client, ClientConfigurationPacketListenerImpl networkHandler, PacketSender responseSender)
			implements ClientConfigurationNetworking.Context {
	}
}
