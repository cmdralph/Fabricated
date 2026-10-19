package io.github.cmdralph.fabricated.neoforge.lifecycle;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Fabric's client chunk events, and the block entity unloads that come with them, including where NeoForge posts
 * nothing: chunks the client drops without {@code ClientChunkCache.drop} (a lower view distance, a packet for a
 * stale slot; see {@code ClientChunkCacheMixin}), and the chunks still loaded when the client drops its level
 * (disconnect, dimension change). Without those, a mod tracking chunks or block entities keeps the old level alive.
 *
 * <p>Only loaded on the physical client; everything here runs on the render thread.
 */
public final class ClientChunkLifecycle {
	/** Chunks of the client's level that got {@code CHUNK_LOAD} and no {@code CHUNK_UNLOAD} yet. */
	private static final Set<LevelChunk> LOADED = Collections.newSetFromMap(new IdentityHashMap<>());

	private ClientChunkLifecycle() {
	}

	public static void chunkLoaded(ClientLevel level, LevelChunk chunk) {
		LOADED.add(chunk);
		ClientChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(level, chunk);
	}

	/** Unloads a chunk once, wherever the client dropped it from. */
	public static void chunkUnloaded(ClientLevel level, LevelChunk chunk) {
		if (!LOADED.remove(chunk)) return;
		BlockEntityLifecycle.unloadAll(chunk);
		ClientChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(level, chunk);
	}

	/** The client drops its level: as upstream, the block entities of its loaded chunks unload (the chunks don't). */
	public static void levelUnloaded(ClientLevel level) {
		for (LevelChunk chunk : List.copyOf(LOADED)) {
			if (chunk.getLevel() != level) continue;
			LOADED.remove(chunk);
			BlockEntityLifecycle.unloadAll(chunk);
		}
	}

	static void blockEntityLoaded(BlockEntity blockEntity, Level level) {
		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.invoker().onLoad(blockEntity, (ClientLevel) level);
	}

	static void blockEntityUnloaded(BlockEntity blockEntity, Level level) {
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.invoker().onUnload(blockEntity, (ClientLevel) level);
	}
}
