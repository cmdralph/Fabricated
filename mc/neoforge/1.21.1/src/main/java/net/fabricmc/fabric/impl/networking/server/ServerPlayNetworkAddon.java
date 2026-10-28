package net.fabricmc.fabric.impl.networking.server;

import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.ChanneledNetworkAddon;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;

/** Play-phase networking state of one player connection. */
public final class ServerPlayNetworkAddon extends ChanneledNetworkAddon<ServerPlayNetworking.PlayPayloadHandler<?>> {
	private volatile @Nullable Set<ResourceLocation> sendable;
	private final ServerGamePacketListenerImpl listener;
	private boolean requestedReconfigure;

	ServerPlayNetworkAddon(ServerGamePacketListenerImpl listener) {
		super(ServerNetworkingImpl.PLAY, listener.getConnection());
		this.listener = listener;
	}

	/** Channels the client accepts, as negotiated by NeoForge, among the payloads Fabric mods registered. */
	@Override
	public Set<ResourceLocation> getSendableChannels() {
		// Channels are negotiated before the play phase, so this is worked out once.
		Set<ResourceLocation> channels = this.sendable;
		if (channels == null) {
			Set<ResourceLocation> found = new HashSet<>();
			for (ResourceLocation id : PayloadTypeRegistryImpl.PLAY_S2C.ids()) {
				if (this.listener.hasChannel(id)) found.add(id);
			}
			this.sendable = channels = Set.copyOf(found);
		}
		return channels;
	}

	@Override
	public Packet<?> createPacket(CustomPacketPayload payload) {
		return ServerNetworkingImpl.createS2CPacket(payload);
	}

	public void reconfigure() {
		if (this.requestedReconfigure) throw new IllegalStateException("Already requested reconfigure");
		this.requestedReconfigure = true;
		ServerNetworkingImpl.RECONFIGURING.add(this.connection);
		this.listener.switchToConfig();
	}

	/** Dispatches a payload NeoForge decoded on the server thread. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void handle(CustomPacketPayload payload) {
		ServerPlayNetworking.PlayPayloadHandler handler = getHandler(payload.type().id());
		if (handler == null) return;
		ServerPlayer player = this.listener.player;
		Context context = new Context(player.level().getServer(), player, this);
		handler.receive(payload, context);
	}

	private record Context(MinecraftServer server, ServerPlayer player, PacketSender responseSender) implements ServerPlayNetworking.Context {
	}
}
