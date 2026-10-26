package io.github.cmdralph.fabricated.testmod;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Native {@code fabric-lifecycle-events-v1}, block entities: one placed in a loaded chunk loads, and unloads when its
 * block is broken (a mod tracking block entities would otherwise keep it, and its level). For the client run, a
 * chest is also placed next to the joining player, so the client has a block entity to unload when it leaves
 * ({@link BlockEntityEventsClientTest}).
 */
public final class BlockEntityEventsTest implements ModInitializer {
	private static final BlockPos POS = new BlockPos(12, 252, 12);

	@Override
	public void onInitialize() {
		// Server thread only.
		Set<BlockEntity> loaded = Collections.newSetFromMap(new IdentityHashMap<>());
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> loaded.add(blockEntity));
		ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> loaded.remove(blockEntity));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			level.setBlockAndUpdate(POS, Blocks.CHEST.defaultBlockState());
			BlockEntity chest = level.getBlockEntity(POS);
			boolean loadedOnPlace = chest != null && loaded.contains(chest);
			level.setBlockAndUpdate(POS, Blocks.AIR.defaultBlockState());
			boolean unloadedOnBreak = chest != null && !loaded.contains(chest);
			if (loadedOnPlace && unloadedOnBreak) {
				TestMod.LOGGER.info("FABRICATED-TEST block entity events OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST block entity events FAILED: loaded on place {}, unloaded on break {}", loadedOnPlace, unloadedOnBreak);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> server.execute(() ->
				listener.player.level().setBlockAndUpdate(listener.player.blockPosition().relative(Direction.SOUTH, 2).above(), Blocks.CHEST.defaultBlockState())));
	}
}
