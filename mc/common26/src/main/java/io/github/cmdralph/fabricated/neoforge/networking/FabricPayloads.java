package io.github.cmdralph.fabricated.neoforge.networking;

import java.util.List;

import net.fabricmc.fabric.api.networking.v1.ClientboundPlayChannelEvents;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** This version's names for Fabric's payload type registries and play channel event, for {@link NetworkingBridge}. */
public final class FabricPayloads {
	static final PayloadTypeRegistryImpl<RegistryFriendlyByteBuf> SERVERBOUND_PLAY = PayloadTypeRegistryImpl.SERVERBOUND_PLAY;
	static final PayloadTypeRegistryImpl<RegistryFriendlyByteBuf> CLIENTBOUND_PLAY = PayloadTypeRegistryImpl.CLIENTBOUND_PLAY;
	static final PayloadTypeRegistryImpl<FriendlyByteBuf> SERVERBOUND_CONFIGURATION = PayloadTypeRegistryImpl.SERVERBOUND_CONFIGURATION;
	static final PayloadTypeRegistryImpl<FriendlyByteBuf> CLIENTBOUND_CONFIGURATION = PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION;

	private FabricPayloads() {
	}

	static void announceChannels(ServerGamePacketListenerImpl listener, ServerPlayNetworkAddon addon, MinecraftServer server, List<Identifier> channels) {
		ClientboundPlayChannelEvents.REGISTER.invoker().onChannelRegister(listener, addon, server, channels);
	}
}
