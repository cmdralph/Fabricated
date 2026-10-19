package io.github.cmdralph.fabricated.neoforge.lifecycle;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Client half of the native {@code fabric-lifecycle-events-v1} (Minecraft 1.21.11). Only loaded on the physical
 * client. {@code CLIENT_STARTED} fires where Fabric API fires it, as {@code Minecraft.run()} starts (before the first
 * frame; the first client tick if that hook is missing);
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
		bus.addListener(GameShuttingDownEvent.class, e -> {
			if (started) ClientLifecycleEvents.CLIENT_STOPPING.invoker().onClientStopping(Minecraft.getInstance());
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

		bus.addListener(ChunkEvent.Load.class, e -> {
			if (!(e.getLevel() instanceof ClientLevel level)) return;
			LevelChunk chunk = e.getChunk();
			ClientChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(level, chunk);
			for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
				ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.invoker().onLoad(blockEntity, level);
			}
		});
		bus.addListener(ChunkEvent.Unload.class, e -> {
			if (!(e.getLevel() instanceof ClientLevel level)) return;
			LevelChunk chunk = e.getChunk();
			for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
				ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.invoker().onUnload(blockEntity, level);
			}
			ClientChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(level, chunk);
		});

		// Last, and not for a join a NeoForge mod cancelled: that entity is never added, so never unloaded either.
		bus.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, e -> {
			if (!e.isCanceled() && e.getLevel() instanceof ClientLevel level) ClientEntityEvents.ENTITY_LOAD.invoker().onLoad(e.getEntity(), level);
		});
		bus.addListener(EntityLeaveLevelEvent.class, e -> {
			if (e.getLevel() instanceof ClientLevel level) ClientEntityEvents.ENTITY_UNLOAD.invoker().onUnload(e.getEntity(), level);
		});
		// NeoForge sends no leave event for the entities of a level the client drops (disconnect, dimension change);
		// a mod tracking entities would keep that level alive.
		bus.addListener(LevelEvent.Unload.class, e -> {
			if (!(e.getLevel() instanceof ClientLevel level)) return;
			for (Entity entity : level.entitiesForRendering()) ClientEntityEvents.ENTITY_UNLOAD.invoker().onUnload(entity, level);
		});
	}
}
