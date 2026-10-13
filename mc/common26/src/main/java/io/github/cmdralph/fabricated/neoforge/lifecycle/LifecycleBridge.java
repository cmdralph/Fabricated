package io.github.cmdralph.fabricated.neoforge.lifecycle;

import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkTicketLevelUpdatedEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Native {@code fabric-lifecycle-events-v1} (Minecraft 26.x): fires the vendored Fabric API events from
 * NeoForge's events instead of upstream's mixins, which conflict with NeoForge's patches.
 *
 * <p>Not fired, because NeoForge has no matching hook: {@code START_DATA_PACK_RELOAD},
 * {@code END_DATA_PACK_RELOAD}, {@code BEFORE_SAVE} and {@code AFTER_SAVE}. Block entity load/unload fire for
 * the block entities of chunks as they load and unload, not for block entities placed or removed in an
 * already-loaded chunk. Full chunk status changes follow NeoForge's ticket level updates. All of this is
 * listed in docs/COMPATIBILITY.md.
 */
public final class LifecycleBridge {
	private static final FullChunkStatus[] STATUSES = FullChunkStatus.values();

	private LifecycleBridge() {
	}

	public static void register(Dist dist) {
		var bus = NeoForge.EVENT_BUS;
		// Before the levels load, as upstream (NeoForge's ServerStartingEvent comes after); after the dynamic registry
		// setup callback, which runs in the same event.
		bus.addListener(EventPriority.LOW, ServerAboutToStartEvent.class, e -> ServerLifecycleEvents.SERVER_STARTING.invoker().onServerStarting(e.getServer()));
		bus.addListener(ServerStartedEvent.class, e -> ServerLifecycleEvents.SERVER_STARTED.invoker().onServerStarted(e.getServer()));
		bus.addListener(ServerStoppingEvent.class, e -> ServerLifecycleEvents.SERVER_STOPPING.invoker().onServerStopping(e.getServer()));
		bus.addListener(ServerStoppedEvent.class, e -> ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(e.getServer()));

		bus.addListener(ServerTickEvent.Pre.class, e -> ServerTickEvents.START_SERVER_TICK.invoker().onStartTick(e.getServer()));
		bus.addListener(ServerTickEvent.Post.class, e -> ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(e.getServer()));
		bus.addListener(LevelTickEvent.Pre.class, e -> {
			if (e.getLevel() instanceof ServerLevel level) ServerTickEvents.START_LEVEL_TICK.invoker().onStartTick(level);
		});
		bus.addListener(LevelTickEvent.Post.class, e -> {
			if (e.getLevel() instanceof ServerLevel level) ServerTickEvents.END_LEVEL_TICK.invoker().onEndTick(level);
		});

		bus.addListener(LevelEvent.Load.class, e -> {
			if (e.getLevel() instanceof ServerLevel level) ServerLevelEvents.LOAD.invoker().onLevelLoad(level.getServer(), level);
		});
		bus.addListener(LevelEvent.Unload.class, e -> {
			if (e.getLevel() instanceof ServerLevel level) ServerLevelEvents.UNLOAD.invoker().onLevelUnload(level.getServer(), level);
		});

		bus.addListener(ChunkEvent.Load.class, e -> {
			if (!(e.getLevel() instanceof ServerLevel level)) return;
			LevelChunk chunk = e.getChunk();
			ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(level, chunk, e.isNewChunk());
			if (e.isNewChunk()) ServerChunkEvents.CHUNK_GENERATE.invoker().onChunkGenerate(level, chunk);
			for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
				ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.invoker().onLoad(blockEntity, level);
			}
			onChunkLoaded(level, chunk);
		});
		bus.addListener(ChunkEvent.Unload.class, e -> {
			if (!(e.getLevel() instanceof ServerLevel level)) return;
			LevelChunk chunk = e.getChunk();
			for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
				ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.invoker().onUnload(blockEntity, level);
			}
			ServerChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(level, chunk);
		});
		bus.addListener(ChunkTicketLevelUpdatedEvent.class, LifecycleBridge::onTicketLevel);

		// Mobs report why they spawned before joining the level; other entities keep a null spawn reason,
		// which Fabric's EntityLoadData allows.
		bus.addListener(EventPriority.LOWEST, FinalizeSpawnEvent.class, e -> EntityLoadDataAccess.setSpawnReason(e.getEntity(), e.getSpawnType()));
		// HIGHEST so that a Fabric veto (ALLOW_LOAD) cancels before NeoForge mods see the entity join.
		bus.addListener(EventPriority.HIGHEST, EntityJoinLevelEvent.class, e -> {
			if (!(e.getLevel() instanceof ServerLevel level)) return;
			Entity entity = e.getEntity();
			EntityLoadDataAccess.set(entity, e.loadedFromDisk());
			if (!ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(entity, level, EntityLoadDataAccess.spawnReason(entity), e.loadedFromDisk())) {
				e.setCanceled(true);
			}
		});
		// LOWEST and only if nobody cancelled: the entity is really joining the level.
		bus.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, e -> {
			if (!e.isCanceled() && e.getLevel() instanceof ServerLevel level) ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(e.getEntity(), level);
		});
		bus.addListener(EntityLeaveLevelEvent.class, e -> {
			if (e.getLevel() instanceof ServerLevel level) ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(e.getEntity(), level);
		});
		bus.addListener(LivingEquipmentChangeEvent.class, e -> {
			LivingEntity entity = e.getEntity();
			if (entity.level() instanceof ServerLevel) {
				ServerEntityEvents.EQUIPMENT_CHANGE.invoker().onChange(entity, e.getSlot(), e.getFrom(), e.getTo());
			}
		});

		bus.addListener(OnDatapackSyncEvent.class, e -> {
			if (e.getPlayer() != null) {
				ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.invoker().onSyncDataPackContents(e.getPlayer(), true);
			} else {
				e.getRelevantPlayers().forEach(player -> ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.invoker().onSyncDataPackContents(player, false));
			}
		});
		bus.addListener(TagsUpdatedEvent.class, e ->
				CommonLifecycleEvents.TAGS_LOADED.invoker().onTagsLoaded(e.getRegistries(), e instanceof TagsUpdatedEvent.ClientPacketReceived));

		if (dist.isClient()) ClientLifecycleBridge.register();
	}

	/** Fires one Fabric event per full-chunk-status step between the old and new ticket levels. */
	private static void onTicketLevel(ChunkTicketLevelUpdatedEvent e) {
		FullChunkStatus from = ChunkLevel.fullStatus(e.getOldTicketLevel());
		FullChunkStatus to = ChunkLevel.fullStatus(e.getNewTicketLevel());
		// Most ticket changes stay within one status (chunks being generated); check that before looking the chunk up.
		if (from == to) return;
		long pos = e.getChunkPos();
		// A chunk that isn't loaded yet gets its promotions when it loads (see onChunkLoaded).
		LevelChunk chunk = e.getLevel().getChunkSource().getChunkNow(ChunkPos.getX(pos), ChunkPos.getZ(pos));
		if (chunk != null) statusChange(e.getLevel(), chunk, from, to);
	}

	/** Promotions up to the status a chunk has when it loads: NeoForge's ticket event came before the chunk existed. */
	private static void onChunkLoaded(ServerLevel level, LevelChunk chunk) {
		statusChange(level, chunk, FullChunkStatus.INACCESSIBLE, chunk.getFullStatus());
	}

	private static void statusChange(ServerLevel level, LevelChunk chunk, FullChunkStatus from, FullChunkStatus to) {
		int step = Integer.signum(to.ordinal() - from.ordinal());
		for (int i = from.ordinal(); i != to.ordinal(); i += step) {
			ServerChunkEvents.FULL_CHUNK_STATUS_CHANGE.invoker().onFullChunkStatusChange(level, chunk, STATUSES[i], STATUSES[i + step]);
		}
	}
}
