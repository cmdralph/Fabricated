package net.fabricmc.fabric.impl.networking.server;

import java.util.Set;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

import net.fabricmc.fabric.api.networking.v1.LoginPacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.impl.networking.ChanneledNetworkAddon;
import net.fabricmc.fabric.mixin.networking.accessor.ServerLoginPacketListenerImplAccessor;

/**
 * Login-phase state. Fabricated does not run Fabric login queries: NeoForge negotiates mods in the configuration
 * phase and exposes no login hooks, so receivers can be registered (mods keep linking) but are never called and
 * no query is sent. docs/COMPATIBILITY.md lists this.
 */
public final class ServerLoginNetworkAddon extends ChanneledNetworkAddon<ServerLoginNetworking.LoginQueryResponseHandler> implements LoginPacketSender {
	ServerLoginNetworkAddon(ServerLoginPacketListenerImpl listener) {
		super(ServerNetworkingImpl.LOGIN, ((ServerLoginPacketListenerImplAccessor) listener).getConnection());
	}

	@Override
	public Set<ResourceLocation> getSendableChannels() {
		return Set.of();
	}

	@Override
	public Packet<?> createPacket(CustomPacketPayload payload) {
		throw new UnsupportedOperationException("Custom payloads cannot be sent during login");
	}

	@Override
	public Packet<?> createPacket(ResourceLocation channelName, FriendlyByteBuf buf) {
		throw new UnsupportedOperationException("Fabric login queries are not supported by Fabricated on NeoForge (" + channelName + ")");
	}
}
