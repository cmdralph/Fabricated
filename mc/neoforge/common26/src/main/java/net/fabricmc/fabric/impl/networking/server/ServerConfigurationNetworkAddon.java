package net.fabricmc.fabric.impl.networking.server;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.fabricmc.fabric.impl.networking.ChanneledNetworkAddon;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.fabricmc.fabric.mixin.networking.accessor.ServerCommonPacketListenerImplAccessor;

/** Configuration-phase networking state of one connection. */
public final class ServerConfigurationNetworkAddon extends ChanneledNetworkAddon<ServerConfigurationNetworking.ConfigurationPacketHandler<?>> {
	private final ServerConfigurationPacketListenerImpl listener;

	ServerConfigurationNetworkAddon(ServerConfigurationPacketListenerImpl listener) {
		super(ServerNetworkingImpl.CONFIGURATION, listener.getConnection());
		this.listener = listener;
	}

	@Override
	public Set<Identifier> getSendableChannels() {
		Set<Identifier> channels = new HashSet<>();
		for (Identifier id : PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.ids()) {
			if (this.listener.hasChannel(id)) channels.add(id);
		}
		return channels;
	}

	@Override
	public Packet<?> createPacket(CustomPacketPayload payload) {
		return ServerNetworkingImpl.createClientboundPacket(payload);
	}

	/** Whether this configuration phase was entered from play through {@code ServerPlayNetworking.reconfigure}. */
	public boolean isReconfiguring() {
		return ServerNetworkingImpl.RECONFIGURING.contains(this.connection);
	}

	public MinecraftServer server() {
		return ((ServerCommonPacketListenerImplAccessor) this.listener).getServer();
	}

	/** Dispatches a payload NeoForge decoded on the network thread (as Fabric documents for this phase). */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void handle(CustomPacketPayload payload) {
		ServerConfigurationNetworking.ConfigurationPacketHandler handler = getHandler(payload.type().id());
		if (handler == null) return;
		Context context = new Context(server(), this.listener, this);
		PacketContext.runWithContext((PacketContextProvider) this.connection, () -> handler.receive(payload, context));
	}

	private record Context(MinecraftServer server, ServerConfigurationPacketListenerImpl packetListener, PacketSender responseSender)
			implements ServerConfigurationNetworking.Context {
	}
}
