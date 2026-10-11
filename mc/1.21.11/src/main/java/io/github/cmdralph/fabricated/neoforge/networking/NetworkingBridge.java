package io.github.cmdralph.fabricated.neoforge.networking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;
import net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon;
import net.fabricmc.fabric.mixin.networking.accessor.ServerCommonPacketListenerImplAccessor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Native {@code fabric-networking-api-v1} (Minecraft 1.21.11): Fabric payload types become optional NeoForge
 * payloads, incoming payloads are dispatched to Fabric's receivers, and Fabric's connection, channel and
 * entity-tracking events fire from NeoForge's events. NeoForge negotiates which channels each side accepts, so
 * Fabric's {@code canSend} reflects that negotiation.
 *
 * <p>Not supported: Fabric login queries ({@code ServerLoginNetworking}, {@code ClientLoginNetworking}).
 */
public final class NetworkingBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/**
	 * The NeoForge configuration-task events being dispatched, by connection, so Fabric's {@code addTask} can join the
	 * right one. Keyed by listener because several players can be configured at the same time.
	 */
	private static final Map<ServerConfigurationPacketListenerImpl, RegisterConfigurationTasksEvent> CURRENT_TASKS = new java.util.concurrent.ConcurrentHashMap<>();

	private NetworkingBridge() {
	}

	/** @param problems receives one line per payload type NeoForge rejected (for the report) */
	public static void register(IEventBus modBus, Dist dist, Consumer<String> problems) {
		modBus.addListener(RegisterPayloadHandlersEvent.class, e -> registerPayloads(e, problems));
		modBus.addListener(RegisterConfigurationTasksEvent.class, NetworkingBridge::configure);

		var bus = NeoForge.EVENT_BUS;
		bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
			if (!(e.getEntity() instanceof ServerPlayer player)) return;
			ServerGamePacketListenerImpl listener = player.connection;
			MinecraftServer server = player.level().getServer();
			ServerPlayNetworkAddon addon = ServerNetworkingImpl.getAddon(listener);
			ServerPlayConnectionEvents.INIT.invoker().onPlayInit(listener, server);
			ServerPlayConnectionEvents.JOIN.invoker().onPlayReady(listener, addon, server);
			List<Identifier> channels = List.copyOf(addon.getSendableChannels());
			if (!channels.isEmpty()) S2CPlayChannelEvents.REGISTER.invoker().onChannelRegister(listener, addon, server, channels);
		});
		bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
			if (e.getEntity() instanceof ServerPlayer player && player.connection != null) {
				ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(player.connection, player.level().getServer());
			}
		});
		bus.addListener(PlayerEvent.StartTracking.class, e -> {
			if (e.getEntity() instanceof ServerPlayer player) EntityTrackingEvents.START_TRACKING.invoker().onStartTracking(e.getTarget(), player);
		});
		bus.addListener(PlayerEvent.StopTracking.class, e -> {
			if (e.getEntity() instanceof ServerPlayer player) EntityTrackingEvents.STOP_TRACKING.invoker().onStopTracking(e.getTarget(), player);
		});

		if (dist.isClient()) ClientNetworkingBridge.register();
	}

	/** Fabric's {@code ServerConfigurationPacketListenerImpl.addTask}; only valid while configuration tasks are collected. */
	public static void addConfigurationTask(ServerConfigurationPacketListenerImpl listener, ConfigurationTask task) {
		RegisterConfigurationTasksEvent event = CURRENT_TASKS.get(listener);
		if (event == null) {
			throw new IllegalStateException("On NeoForge, Fabric configuration tasks can only be added from "
					+ "ServerConfigurationConnectionEvents.BEFORE_CONFIGURE or CONFIGURE (task " + task.type().id() + ")");
		}
		event.register(task);
	}

	private static void configure(RegisterConfigurationTasksEvent event) {
		if (!(event.getListener() instanceof ServerConfigurationPacketListenerImpl listener)) return;
		MinecraftServer server = ((ServerCommonPacketListenerImplAccessor) listener).getServer();
		CURRENT_TASKS.put(listener, event);
		try {
			ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.invoker().onSendConfiguration(listener, server);
			ServerConfigurationConnectionEvents.CONFIGURE.invoker().onSendConfiguration(listener, server);
		} finally {
			CURRENT_TASKS.remove(listener);
		}
	}

	private static void registerPayloads(RegisterPayloadHandlersEvent event, Consumer<String> problems) {
		PayloadRegistrar play = event.registrar("1").optional();
		// Fabric runs configuration handlers on the network thread and play handlers on the main thread.
		PayloadRegistrar configuration = play.executesOn(HandlerThread.NETWORK);
		int count = registerPhase(PayloadTypeRegistryImpl.PLAY_C2S, PayloadTypeRegistryImpl.PLAY_S2C, true, play, problems)
				+ registerPhase(PayloadTypeRegistryImpl.CONFIGURATION_C2S, PayloadTypeRegistryImpl.CONFIGURATION_S2C, false, configuration, problems);
		if (count > 0) LOGGER.info("Registered {} Fabric payload type(s) with NeoForge", count);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <B extends FriendlyByteBuf> int registerPhase(PayloadTypeRegistryImpl<B> serverbound, PayloadTypeRegistryImpl<B> clientbound,
			boolean play, PayloadRegistrar registrar, Consumer<String> problems) {
		Map<Identifier, CustomPacketPayload.TypeAndCodec> toServer = new LinkedHashMap<>();
		Map<Identifier, CustomPacketPayload.TypeAndCodec> toClient = new LinkedHashMap<>();
		serverbound.lock().forEach(t -> toServer.put(t.type().id(), t));
		clientbound.lock().forEach(t -> toClient.put(t.type().id(), t));
		List<Identifier> ids = new ArrayList<>(toServer.keySet());
		toClient.keySet().stream().filter(id -> !toServer.containsKey(id)).forEach(ids::add);

		IPayloadHandler serverHandler = play ? NetworkingBridge::handleServerPlay : NetworkingBridge::handleServerConfiguration;
		// Lambdas, not method references: the client bridge must only be loaded when a clientbound payload
		// is actually handled, which never happens on a dedicated server.
		IPayloadHandler clientHandler = play
				? (payload, context) -> ClientNetworkingBridge.handlePlay((CustomPacketPayload) payload, context)
				: (payload, context) -> ClientNetworkingBridge.handleConfiguration((CustomPacketPayload) payload, context);
		int count = 0;
		for (Identifier id : ids) {
			CustomPacketPayload.TypeAndCodec s = toServer.get(id);
			CustomPacketPayload.TypeAndCodec c = toClient.get(id);
			try {
				if (s != null && c != null) {
					if (s.codec() != c.codec()) {
						// NeoForge keeps one codec per payload id; Fabric allows one per direction.
						problems.accept(id + ": registered with different codecs per direction; NeoForge uses the serverbound one for both");
					}
					StreamCodec codec = s.codec();
					if (play) registrar.playBidirectional(s.type(), codec, serverHandler, clientHandler);
					else registrar.configurationBidirectional(s.type(), codec, serverHandler, clientHandler);
				} else if (s != null) {
					if (play) registrar.playToServer(s.type(), s.codec(), serverHandler);
					else registrar.configurationToServer(s.type(), s.codec(), serverHandler);
				} else {
					if (play) registrar.playToClient(c.type(), c.codec(), clientHandler);
					else registrar.configurationToClient(c.type(), c.codec(), clientHandler);
				}
				count++;
			} catch (RuntimeException e) {
				// For example, a NeoForge mod already registered the same payload id.
				problems.accept(id + ": NeoForge rejected the payload type (" + e.getMessage() + "); Fabric mods using it cannot exchange it");
				LOGGER.error("NeoForge rejected Fabric payload type {}", id, e);
			}
		}
		return count;
	}

	private static void handleServerPlay(CustomPacketPayload payload, IPayloadContext context) {
		if (context.listener() instanceof ServerGamePacketListenerImpl listener) ServerNetworkingImpl.getAddon(listener).handle(payload);
	}

	private static void handleServerConfiguration(CustomPacketPayload payload, IPayloadContext context) {
		if (context.listener() instanceof ServerConfigurationPacketListenerImpl listener) ServerNetworkingImpl.getAddon(listener).handle(payload);
	}

	/** Channels a Fabric mod can currently send to {@code player} (for diagnostics). */
	public static Set<Identifier> sendable(ServerPlayer player) {
		return ServerNetworkingImpl.getAddon(player.connection).getSendableChannels();
	}
}
