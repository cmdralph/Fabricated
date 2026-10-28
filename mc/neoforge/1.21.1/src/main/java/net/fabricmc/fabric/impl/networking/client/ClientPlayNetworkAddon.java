package net.fabricmc.fabric.impl.networking.client;

import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.impl.networking.ChanneledNetworkAddon;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;

/** Client play-phase networking state. */
public final class ClientPlayNetworkAddon extends ChanneledNetworkAddon<ClientPlayNetworking.PlayPayloadHandler<?>> {
	private volatile @Nullable Set<ResourceLocation> sendable;
	private final ClientPacketListener listener;

	ClientPlayNetworkAddon(ClientPacketListener listener) {
		super(ClientNetworkingImpl.PLAY, listener.getConnection());
		this.listener = listener;
	}

	@Override
	public Set<ResourceLocation> getSendableChannels() {
		// Channels are negotiated before the play phase, so this is worked out once.
		Set<ResourceLocation> channels = this.sendable;
		if (channels == null) {
			Set<ResourceLocation> found = new HashSet<>();
			for (ResourceLocation id : PayloadTypeRegistryImpl.PLAY_C2S.ids()) {
				if (this.listener.hasChannel(id)) found.add(id);
			}
			this.sendable = channels = Set.copyOf(found);
		}
		return channels;
	}

	@Override
	public Packet<?> createPacket(CustomPacketPayload payload) {
		return ClientNetworkingImpl.createC2SPacket(payload);
	}

	/** Dispatches a payload NeoForge decoded on the render thread. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void handle(CustomPacketPayload payload) {
		ClientPlayNetworking.PlayPayloadHandler handler = getHandler(payload.type().id());
		if (handler == null) return;
		Minecraft client = Minecraft.getInstance();
		Context context = new Context(client, client.player, this);
		handler.receive(payload, context);
	}

	private record Context(Minecraft client, LocalPlayer player, PacketSender responseSender) implements ClientPlayNetworking.Context {
	}
}
