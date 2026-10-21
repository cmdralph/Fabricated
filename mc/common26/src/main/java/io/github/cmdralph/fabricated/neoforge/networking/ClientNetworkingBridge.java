package io.github.cmdralph.fabricated.neoforge.networking;

import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ServerboundPlayChannelEvents;
import net.fabricmc.fabric.impl.networking.client.ClientNetworkingImpl;
import net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.handling.ClientPayloadContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.payload.MinecraftRegisterPayload;

/** Client half of the native networking module. Only loaded on the physical client. */
public final class ClientNetworkingBridge {
	private ClientNetworkingBridge() {
	}

	static void register() {
		var bus = NeoForge.EVENT_BUS;
		bus.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> {
			Minecraft client = Minecraft.getInstance();
			ClientConfigurationPacketListenerImpl configuration = ClientNetworkingImpl.currentConfiguration();
			if (configuration != null) {
				ClientConfigurationConnectionEvents.COMPLETE.invoker().onConfigurationComplete(configuration, client);
				ClientConfigurationConnectionEvents.READY.invoker().onConfigurationReady(configuration, client);
				ClientNetworkingImpl.setCurrentConfiguration(null);
			}
			ClientPacketListener listener = e.getPlayer().connection;
			ClientPlayNetworkAddon addon = ClientNetworkingImpl.getAddon(listener);
			ClientPlayConnectionEvents.INIT.invoker().onPlayInit(listener, client);
			ClientPlayConnectionEvents.JOIN.invoker().onPlayReady(listener, addon, client);
			// The server learns which Fabric payloads in the minecraft namespace the client receives (NET-4).
			Set<Identifier> vanilla = NetworkingBridge.vanillaNamespaceIds(FabricPayloads.CLIENTBOUND_PLAY);
			if (!vanilla.isEmpty()) listener.send(new ServerboundCustomPayloadPacket(new MinecraftRegisterPayload(vanilla)));
			List<Identifier> channels = List.copyOf(addon.getSendableChannels());
			if (!channels.isEmpty()) ServerboundPlayChannelEvents.REGISTER.invoker().onChannelRegister(listener, addon, client, channels);
		});
		bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
			// Also ends a configuration phase that failed (a kick, a mod mismatch) before play started.
			ClientNetworkingImpl.setCurrentConfiguration(null);
			if (e.getPlayer() != null) ClientPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(e.getPlayer().connection, Minecraft.getInstance());
		});
	}

	/** Called from the configuration listener's constructor (see {@code ClientConfigurationPacketListenerImplMixin}). */
	public static void onConfigurationStart(ClientConfigurationPacketListenerImpl listener) {
		ClientNetworkingImpl.setCurrentConfiguration(listener);
		Minecraft client = Minecraft.getInstance();
		ClientConfigurationConnectionEvents.INIT.invoker().onConfigurationInit(listener, client);
		ClientConfigurationConnectionEvents.START.invoker().onConfigurationStart(listener, client);
	}

	/** Called when the connection closes during configuration (see {@code ClientConfigurationPacketListenerImplMixin}). */
	public static void onConfigurationDisconnect(ClientConfigurationPacketListenerImpl listener) {
		if (ClientNetworkingImpl.currentConfiguration() == listener) ClientNetworkingImpl.setCurrentConfiguration(null);
		ClientConfigurationConnectionEvents.DISCONNECT.invoker().onConfigurationDisconnect(listener, Minecraft.getInstance());
	}

	/**
	 * Hands a Fabric payload in the {@code minecraft} namespace to Fabric's receivers, which NeoForge's modded payload
	 * handling skips: play payloads on the client thread, configuration ones on the network thread (NET-4). False for any
	 * other payload.
	 */
	public static boolean receiveVanillaNamespace(ClientCommonPacketListener listener, CustomPacketPayload payload) {
		if (!NetworkingBridge.inVanillaNamespace(payload)) return false;
		ClientPayloadContext context = new ClientPayloadContext(listener, payload.type().id());
		if (listener instanceof ClientPacketListener) context.enqueueWork(() -> handlePlay(payload, context));
		else handleConfiguration(payload, context);
		return true;
	}

	static void handlePlay(CustomPacketPayload payload, IPayloadContext context) {
		if (context.listener() instanceof ClientPacketListener listener) ClientNetworkingImpl.getAddon(listener).handle(payload);
	}

	static void handleConfiguration(CustomPacketPayload payload, IPayloadContext context) {
		if (context.listener() instanceof ClientConfigurationPacketListenerImpl listener) ClientNetworkingImpl.getAddon(listener).handle(payload);
	}
}
