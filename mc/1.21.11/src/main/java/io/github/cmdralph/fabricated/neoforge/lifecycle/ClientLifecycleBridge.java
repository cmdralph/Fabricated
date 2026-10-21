package io.github.cmdralph.fabricated.neoforge.lifecycle;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Client half of the native {@code fabric-lifecycle-events-v1} (Minecraft 1.21.11). Only loaded on the physical
 * client. {@code CLIENT_STARTED} fires where Fabric API fires it, as {@code Minecraft.run()} starts (before the first
 * frame; the first client tick if that hook is missing), and {@code CLIENT_STOPPING} where it fires it too, as
 * {@code Minecraft.destroy()} starts (NeoForge's {@code ClientStoppingEvent}: its {@code GameShuttingDownEvent} comes
 * only from {@code Minecraft.stop()} while the game runs, so not for a game whose loop ended otherwise);
 * {@code AFTER_CLIENT_WORLD_CHANGE} fires when the player logs in and when respawning into another level.
 */
public final class ClientLifecycleBridge {
	private static boolean started;

	private ClientLifecycleBridge() {
	}

	/** Fires {@code CLIENT_STARTED} once; called as {@code Minecraft.run()} starts (see {@code MinecraftStartedMixin}). */
	public static void clientStarted(Minecraft client) {
		if (started) return;
		started = true;
		ClientLifecycleEvents.CLIENT_STARTED.invoker().onClientStarted(client);
	}

	static void register() {
		var bus = NeoForge.EVENT_BUS;
		bus.addListener(ClientTickEvent.Pre.class, e -> {
			Minecraft client = Minecraft.getInstance();
			clientStarted(client);
			ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(client);
		});
		bus.addListener(ClientTickEvent.Post.class, e -> ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(Minecraft.getInstance()));
		bus.addListener(ClientStoppingEvent.class, e -> {
			if (started) ClientLifecycleEvents.CLIENT_STOPPING.invoker().onClientStopping(e.getClient());
		});

		bus.addListener(LevelTickEvent.Pre.class, e -> {
			if (e.getLevel() instanceof ClientLevel level) ClientTickEvents.START_WORLD_TICK.invoker().onStartTick(level);
		});
		bus.addListener(LevelTickEvent.Post.class, e -> {
			if (e.getLevel() instanceof ClientLevel level) ClientTickEvents.END_WORLD_TICK.invoker().onEndTick(level);
		});

		bus.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> {
			if (e.getPlayer().level() instanceof ClientLevel level) {
				ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.invoker().afterWorldChange(Minecraft.getInstance(), level);
			}
		});
		bus.addListener(ClientPlayerNetworkEvent.Clone.class, e -> {
			if (e.getNewPlayer().level() != e.getOldPlayer().level() && e.getNewPlayer().level() instanceof ClientLevel level) {
				ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.invoker().afterWorldChange(Minecraft.getInstance(), level);
			}
		});

		// Block entities load as they enter their chunk (LevelChunkBlockEntityMixin), loading with it or later.
		bus.addListener(ChunkEvent.Load.class, e -> {
			if (e.getLevel() instanceof ClientLevel level) ClientChunkLifecycle.chunkLoaded(level, e.getChunk());
		});
		bus.addListener(ChunkEvent.Unload.class, e -> {
			if (e.getLevel() instanceof ClientLevel level) ClientChunkLifecycle.chunkUnloaded(level, e.getChunk());
		});

		// Last, and not for a join a NeoForge mod cancelled: that entity is never added, so never unloaded either.
		bus.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, e -> {
			if (!e.isCanceled() && e.getLevel() instanceof ClientLevel level) ClientEntityEvents.ENTITY_LOAD.invoker().onLoad(e.getEntity(), level);
		});
		bus.addListener(EntityLeaveLevelEvent.class, e -> {
			if (e.getLevel() instanceof ClientLevel level) ClientEntityEvents.ENTITY_UNLOAD.invoker().onUnload(e.getEntity(), level);
		});
		// NeoForge sends no leave event for the entities and block entities of a level the client drops (disconnect,
		// dimension change); a mod tracking them would keep that level alive.
		bus.addListener(LevelEvent.Unload.class, e -> {
			if (!(e.getLevel() instanceof ClientLevel level)) return;
			for (Entity entity : level.entitiesForRendering()) ClientEntityEvents.ENTITY_UNLOAD.invoker().onUnload(entity, level);
			ClientChunkLifecycle.levelUnloaded(level);
		});
	}
}
