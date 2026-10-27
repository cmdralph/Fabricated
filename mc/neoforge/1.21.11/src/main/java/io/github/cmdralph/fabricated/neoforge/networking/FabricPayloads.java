package io.github.cmdralph.fabricated.neoforge.networking;

import java.util.List;

import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * This version's names for Fabric's payload type registries and play channel event, and its registrar's call for a
 * payload sent both ways, for {@link NetworkingBridge}.
 */
public final class FabricPayloads {
	static final PayloadTypeRegistryImpl<RegistryFriendlyByteBuf> SERVERBOUND_PLAY = PayloadTypeRegistryImpl.PLAY_C2S;
	static final PayloadTypeRegistryImpl<RegistryFriendlyByteBuf> CLIENTBOUND_PLAY = PayloadTypeRegistryImpl.PLAY_S2C;
	static final PayloadTypeRegistryImpl<FriendlyByteBuf> SERVERBOUND_CONFIGURATION = PayloadTypeRegistryImpl.CONFIGURATION_C2S;
	static final PayloadTypeRegistryImpl<FriendlyByteBuf> CLIENTBOUND_CONFIGURATION = PayloadTypeRegistryImpl.CONFIGURATION_S2C;

	private FabricPayloads() {
	}

	static void announceChannels(ServerGamePacketListenerImpl listener, ServerPlayNetworkAddon addon, MinecraftServer server, List<Identifier> channels) {
		S2CPlayChannelEvents.REGISTER.invoker().onChannelRegister(listener, addon, server, channels);
	}

	/**
	 * Registers a payload type sent both ways, each side handling it with its own handler. Raw types, as the play and
	 * configuration codecs read different buffers.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	static void bidirectional(PayloadRegistrar registrar, boolean play, CustomPacketPayload.Type type, StreamCodec codec, IPayloadHandler serverHandler,
			IPayloadHandler clientHandler) {
		if (play) registrar.playBidirectional(type, codec, serverHandler, clientHandler);
		else registrar.configurationBidirectional(type, codec, serverHandler, clientHandler);
	}
}
