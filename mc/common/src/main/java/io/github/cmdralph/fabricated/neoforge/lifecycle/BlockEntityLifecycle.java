package io.github.cmdralph.fabricated.neoforge.lifecycle;

import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Fabric's block entity load and unload events, fired where upstream fires them: when a block entity enters or leaves
 * its chunk ({@code LevelChunkBlockEntityMixin}), whether the chunk is loading or was loaded long before, and for the
 * block entities a chunk still holds when it unloads. Firing them only for whole chunks, as NeoForge's chunk events
 * allow, would never unload a block entity broken in a loaded chunk, so a mod tracking block entities would keep it,
 * and its level, forever.
 */
public final class BlockEntityLifecycle {
	private BlockEntityLifecycle() {
	}

	public static void loaded(BlockEntity blockEntity, Level level) {
		if (level instanceof ServerLevel serverLevel) {
			ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.invoker().onLoad(blockEntity, serverLevel);
		} else if (level.isClientSide()) {
			ClientChunkLifecycle.blockEntityLoaded(blockEntity, level);
		}
	}

	public static void unloaded(BlockEntity blockEntity, Level level) {
		if (level instanceof ServerLevel serverLevel) {
			ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.invoker().onUnload(blockEntity, serverLevel);
		} else if (level.isClientSide()) {
			ClientChunkLifecycle.blockEntityUnloaded(blockEntity, level);
		}
	}

	/** Unloads the block entities a chunk still holds (from a copy: a listener may change the chunk). */
	public static void unloadAll(LevelChunk chunk) {
		Level level = chunk.getLevel();
		for (BlockEntity blockEntity : List.copyOf(chunk.getBlockEntities().values())) unloaded(blockEntity, level);
	}
}
