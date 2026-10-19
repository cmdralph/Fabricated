package io.github.cmdralph.fabricated.testmod;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Client half of {@link BlockEntityEventsTest}: once the client has left its world (mc-runtime-test disconnects
 * before quitting), every block entity it loaded, the chest next to the player at least, has been unloaded. NeoForge
 * posts no unload for the block entities of a level the client drops.
 */
public final class BlockEntityEventsClientTest implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Render thread only.
		Set<BlockEntity> loaded = Collections.newSetFromMap(new IdentityHashMap<>());
		int[] loads = {0};
		boolean[] reported = {false};
		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
			loaded.add(blockEntity);
			loads[0]++;
		});
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> loaded.remove(blockEntity));
		// The end of the tick in which the client dropped its level.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (reported[0] || client.level != null || loads[0] == 0) return;
			reported[0] = true;
			if (loaded.isEmpty()) {
				TestMod.LOGGER.info("FABRICATED-TEST client block entity events OK ({} loaded and unloaded)", loads[0]);
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST client block entity events FAILED: {} loaded, {} never unloaded", loads[0], loaded.size());
			}
		});
	}
}
