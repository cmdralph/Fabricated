package io.github.cmdralph.fabricated.neoforge.networking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.neoforge.mixin.NetworkRegistryAccessor;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;
import net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon;
import net.fabricmc.fabric.mixin.networking.accessor.ServerCommonPacketListenerImplAccessor;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Native {@code fabric-networking-api-v1}: Fabric payload types become optional NeoForge
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
	private static final Map<ServerConfigurationPacketListenerImpl, RegisterConfigurationTasksEvent> CURRENT_TASKS = new ConcurrentHashMap<>();

	private NetworkingBridge() {
	}

	/** @param problems receives one line per payload type NeoForge rejected (for the report) */
	public static void register(IEventBus modBus, Dist dist, Consumer<String> problems) {
		modBus.addListener(RegisterPayloadHandlersEvent.class, e -> registerPayloads(e, problems));
		modBus.addListener(RegisterConfigurationTasksEvent.class, NetworkingBridge::configure);

		var bus = NeoForge.EVENT_BUS;
		// Before ServerPlayerEvents.JOIN (fired from the same NeoForge event), as upstream's INIT comes first.
		bus.addListener(EventPriority.HIGH, PlayerEvent.PlayerLoggedInEvent.class, e -> {
			if (!(e.getEntity() instanceof ServerPlayer player)) return;
			ServerGamePacketListenerImpl listener = player.connection;
			MinecraftServer server = player.level().getServer();
			ServerPlayNetworkAddon addon = ServerNetworkingImpl.getAddon(listener);
			ServerPlayConnectionEvents.INIT.invoker().onPlayInit(listener, server);
			ServerPlayConnectionEvents.JOIN.invoker().onPlayReady(listener, addon, server);
			List<Identifier> channels = List.copyOf(addon.getSendableChannels());
			if (!channels.isEmpty()) FabricPayloads.announceChannels(listener, addon, server, channels);
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

	/** Called when a connection closes during configuration (see {@code ServerCommonPacketListenerDisconnectMixin}). */
	public static void onConfigurationDisconnect(ServerConfigurationPacketListenerImpl listener) {
		ServerConfigurationConnectionEvents.DISCONNECT.invoker().onConfigureDisconnect(listener, ((ServerCommonPacketListenerImplAccessor) listener).getServer());
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
		int count = registerPhase(new Phase(FabricPayloads.SERVERBOUND_PLAY, FabricPayloads.CLIENTBOUND_PLAY, true, play, problems,
				ConcurrentHashMap.newKeySet()))
				+ registerPhase(new Phase(FabricPayloads.SERVERBOUND_CONFIGURATION, FabricPayloads.CLIENTBOUND_CONFIGURATION, false, configuration,
						problems, ConcurrentHashMap.newKeySet()));
		if (count > 0) LOGGER.info("Registered {} Fabric payload type(s) with NeoForge", count);
	}

	/**
	 * One protocol's Fabric payload types, both directions, the NeoForge registrar they go to and the ids NeoForge took
	 * from it.
	 */
	private record Phase(PayloadTypeRegistryImpl<?> serverbound, PayloadTypeRegistryImpl<?> clientbound, boolean play,
			PayloadRegistrar registrar, Consumer<String> problems, Set<Identifier> registered) {
		ConnectionProtocol protocol() {
			return play ? ConnectionProtocol.PLAY : ConnectionProtocol.CONFIGURATION;
		}
	}

	private static int registerPhase(Phase phase) {
		List<Identifier> ids = new ArrayList<>();
		phase.serverbound().lock(id -> registerLate(phase, id)).forEach(t -> ids.add(t.type().id()));
		phase.clientbound().lock(id -> registerLate(phase, id)).forEach(t -> {
			if (!ids.contains(t.type().id())) ids.add(t.type().id());
		});
		int count = 0;
		for (Identifier id : ids) {
			if (register(phase, id)) count++;
		}
		return count;
	}

	/**
	 * A payload type registered after NeoForge's registration event, which upstream accepts at any time (NET-3):
	 * MiniHUD registers its own from the end of {@code Minecraft}'s constructor, Ledger as the server starts. NeoForge's
	 * registry takes it while reopened. An id NeoForge already has for the other direction is registered again, for
	 * both: NeoForge keeps one registration per id. An id a NeoForge mod has is left to it. No connection is open yet when
	 * mods register this late.
	 */
	private static synchronized void registerLate(Phase phase, Identifier id) {
		if (phase.registered().remove(id)) {
			NetworkRegistryAccessor.fabricated$registrations().get(phase.protocol()).remove(id);
			NetworkRegistryAccessor.fabricated$serverboundHandlers().get(phase.protocol()).remove(id);
			NetworkRegistryAccessor.fabricated$clientboundHandlers().get(phase.protocol()).remove(id);
		}
		boolean setup = NetworkRegistryAccessor.fabricated$setup();
		NetworkRegistryAccessor.fabricated$setSetup(false);
		try {
			if (register(phase, id)) LOGGER.info("Registered Fabric payload type {} with NeoForge after its registration event", id);
		} finally {
			NetworkRegistryAccessor.fabricated$setSetup(setup);
		}
	}

	/** Registers an id's Fabric payload types (one per direction) with NeoForge; false if NeoForge refused them. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static boolean register(Phase phase, Identifier id) {
		CustomPacketPayload.TypeAndCodec s = phase.serverbound().get(id);
		CustomPacketPayload.TypeAndCodec c = phase.clientbound().get(id);
		boolean play = phase.play();
		PayloadRegistrar registrar = phase.registrar();
		IPayloadHandler serverHandler = play ? NetworkingBridge::handleServerPlay : NetworkingBridge::handleServerConfiguration;
		// Lambdas, not method references: the client bridge must only be loaded when a clientbound payload
		// is actually handled, which never happens on a dedicated server.
		IPayloadHandler clientHandler = play
				? (payload, context) -> ClientNetworkingBridge.handlePlay((CustomPacketPayload) payload, context)
				: (payload, context) -> ClientNetworkingBridge.handleConfiguration((CustomPacketPayload) payload, context);
		try {
			if (s != null && c != null) {
				// NeoForge keeps one codec per payload id; Fabric allows one per direction (see ClientboundCodecs).
				if (s.codec() != c.codec()) ClientboundCodecs.put(phase.protocol(), id, c.codec());
				if (play) registrar.playBidirectional(s.type(), s.codec(), serverHandler, clientHandler);
				else registrar.configurationBidirectional(s.type(), s.codec(), serverHandler, clientHandler);
			} else if (s != null) {
				if (play) registrar.playToServer(s.type(), s.codec(), serverHandler);
				else registrar.configurationToServer(s.type(), s.codec(), serverHandler);
			} else {
				if (play) registrar.playToClient(c.type(), c.codec(), clientHandler);
				else registrar.configurationToClient(c.type(), c.codec(), clientHandler);
			}
			phase.registered().add(id);
			return true;
		} catch (RuntimeException e) {
			// For example, a NeoForge mod already registered the same payload id.
			phase.problems().accept(id + ": NeoForge rejected the payload type (" + e.getMessage() + "); Fabric mods using it cannot exchange it");
			LOGGER.error("NeoForge rejected Fabric payload type {}", id, e);
			return false;
		}
	}

	/** Whether {@code id} is a Fabric mod's payload type that the connection behind {@code listener} has no channel for: Fabric doesn't send those (NeoForge throws). */
	public static boolean unsendable(Identifier id, ICommonPacketListener listener, boolean clientbound) {
		boolean fabric = clientbound
				? FabricPayloads.CLIENTBOUND_PLAY.get(id) != null || FabricPayloads.CLIENTBOUND_CONFIGURATION.get(id) != null
				: FabricPayloads.SERVERBOUND_PLAY.get(id) != null || FabricPayloads.SERVERBOUND_CONFIGURATION.get(id) != null;
		return fabric && !NetworkRegistry.hasChannel(listener, id);
	}

	/** Whether NeoForge has no registration for {@code id} in any protocol: no NeoForge or Fabric mod declared it (NET-5). */
	public static boolean unregistered(Identifier id) {
		for (Map<Identifier, ?> registrations : NetworkRegistryAccessor.fabricated$registrations().values()) {
			if (registrations.containsKey(id)) return false;
		}
		return true;
	}

	private static void handleServerPlay(CustomPacketPayload payload, IPayloadContext context) {
		if (context.listener() instanceof ServerGamePacketListenerImpl listener) ServerNetworkingImpl.getAddon(listener).handle(payload);
	}

	private static void handleServerConfiguration(CustomPacketPayload payload, IPayloadContext context) {
		if (context.listener() instanceof ServerConfigurationPacketListenerImpl listener) ServerNetworkingImpl.getAddon(listener).handle(payload);
	}
}
