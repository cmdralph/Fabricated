package io.github.cmdralph.fabricated.neoforge.networking;

import java.util.List;

import io.github.cmdralph.fabricated.neoforge.mixin.NetworkRegistryHandlersAccessor;
import net.fabricmc.fabric.api.networking.v1.ClientboundPlayChannelEvents;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon;
import net.minecraft.network.ConnectionProtocol;
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
	static final PayloadTypeRegistryImpl<RegistryFriendlyByteBuf> SERVERBOUND_PLAY = PayloadTypeRegistryImpl.SERVERBOUND_PLAY;
	static final PayloadTypeRegistryImpl<RegistryFriendlyByteBuf> CLIENTBOUND_PLAY = PayloadTypeRegistryImpl.CLIENTBOUND_PLAY;
	static final PayloadTypeRegistryImpl<FriendlyByteBuf> SERVERBOUND_CONFIGURATION = PayloadTypeRegistryImpl.SERVERBOUND_CONFIGURATION;
	static final PayloadTypeRegistryImpl<FriendlyByteBuf> CLIENTBOUND_CONFIGURATION = PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION;

	private FabricPayloads() {
	}

	/** Forgets NeoForge's handlers of a payload type registered again (NET-3); NeoForge keeps them beside the registrations. */
	static void forgetHandlers(ConnectionProtocol protocol, Identifier id) {
		NetworkRegistryHandlersAccessor.fabricated$serverboundHandlers().get(protocol).remove(id);
		NetworkRegistryHandlersAccessor.fabricated$clientboundHandlers().get(protocol).remove(id);
	}

	static void announceChannels(ServerGamePacketListenerImpl listener, ServerPlayNetworkAddon addon, MinecraftServer server, List<Identifier> channels) {
		ClientboundPlayChannelEvents.REGISTER.invoker().onChannelRegister(listener, addon, server, channels);
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
