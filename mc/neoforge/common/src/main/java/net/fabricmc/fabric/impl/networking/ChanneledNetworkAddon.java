package net.fabricmc.fabric.impl.networking;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelFutureListener;
import org.jspecify.annotations.Nullable;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.networking.v1.PacketSender;

/**
 * Per-connection state of one networking phase: the receivers registered for this connection only, and the
 * {@link PacketSender} used to reply. Which channels the other side can receive is answered by NeoForge's own
 * channel negotiation (see subclasses), not by Fabric's {@code minecraft:register} packets.
 *
 * @param <H> handler type of the phase
 */
public abstract class ChanneledNetworkAddon<H> implements PacketSender {
	protected final GlobalReceiverRegistry<H> globalReceivers;
	protected final Connection connection;
	private final Map<Identifier, H> receivers = new ConcurrentHashMap<>();

	protected ChanneledNetworkAddon(GlobalReceiverRegistry<H> globalReceivers, Connection connection) {
		this.globalReceivers = globalReceivers;
		this.connection = connection;
	}

	public boolean registerChannel(Identifier channelName, H handler) {
		Objects.requireNonNull(channelName, "Channel name cannot be null");
		Objects.requireNonNull(handler, "Packet handler cannot be null");
		return this.receivers.putIfAbsent(channelName, handler) == null;
	}

	public @Nullable H unregisterChannel(Identifier channelName) {
		Objects.requireNonNull(channelName, "Channel name cannot be null");
		return this.receivers.remove(channelName);
	}

	/** Handler for an incoming payload: this connection's own receiver first, then the global one. */
	public @Nullable H getHandler(Identifier channelName) {
		H handler = this.receivers.get(channelName);
		return handler != null ? handler : this.globalReceivers.getHandler(channelName);
	}

	public Set<Identifier> getReceivableChannels() {
		Set<Identifier> channels = new HashSet<>(this.globalReceivers.getChannels());
		channels.addAll(this.receivers.keySet());
		return channels;
	}

	public abstract Set<Identifier> getSendableChannels();

	@Override
	public void sendPacket(Packet<?> packet, @Nullable ChannelFutureListener callback) {
		Objects.requireNonNull(packet, "Packet cannot be null");
		this.connection.send(packet, callback);
	}

	@Override
	public void disconnect(Component disconnectReason) {
		Objects.requireNonNull(disconnectReason, "Disconnect reason cannot be null");
		this.connection.disconnect(disconnectReason);
	}
}
