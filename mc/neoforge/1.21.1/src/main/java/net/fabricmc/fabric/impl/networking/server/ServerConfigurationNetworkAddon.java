package net.fabricmc.fabric.impl.networking.server;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.impl.networking.ChanneledNetworkAddon;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.fabricmc.fabric.mixin.networking.accessor.ServerCommonNetworkHandlerAccessor;

/** Configuration-phase networking state of one connection. */
public final class ServerConfigurationNetworkAddon extends ChanneledNetworkAddon<ServerConfigurationNetworking.ConfigurationPacketHandler<?>> {
	private final ServerConfigurationPacketListenerImpl listener;

	ServerConfigurationNetworkAddon(ServerConfigurationPacketListenerImpl listener) {
		super(ServerNetworkingImpl.CONFIGURATION, listener.getConnection());
		this.listener = listener;
	}

	@Override
	public Set<ResourceLocation> getSendableChannels() {
		Set<ResourceLocation> channels = new HashSet<>();
		for (ResourceLocation id : PayloadTypeRegistryImpl.CONFIGURATION_S2C.ids()) {
			if (this.listener.hasChannel(id)) channels.add(id);
		}
		return channels;
	}

	@Override
	public Packet<?> createPacket(CustomPacketPayload payload) {
		return ServerNetworkingImpl.createS2CPacket(payload);
	}

	/** Whether this configuration phase was entered from play through {@code ServerPlayNetworking.reconfigure}. */
	public boolean isReconfiguring() {
		return ServerNetworkingImpl.RECONFIGURING.contains(this.connection);
	}

	public MinecraftServer server() {
		return ((ServerCommonNetworkHandlerAccessor) this.listener).getServer();
	}

	/** Dispatches a payload NeoForge decoded on the network thread (as Fabric documents for this phase). */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void handle(CustomPacketPayload payload) {
		ServerConfigurationNetworking.ConfigurationPacketHandler handler = getHandler(payload.type().id());
		if (handler == null) return;
		Context context = new Context(server(), this.listener, this);
		handler.receive(payload, context);
	}

	private record Context(MinecraftServer server, ServerConfigurationPacketListenerImpl networkHandler, PacketSender responseSender)
			implements ServerConfigurationNetworking.Context {
	}
}
