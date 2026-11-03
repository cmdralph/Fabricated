package io.github.cmdralph.fabricated.testmod;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Client half of {@link BlockEntityEventsTest}: once the client has left its world (mc-runtime-test disconnects
 * before quitting), every block entity it loaded, the chest next to the player at least, has been unloaded. NeoForge
 * posts no unload for the block entities of a level the client drops. Checked at the end of the tick the client left
 * its world in, or as it stops, for a client that quit before that tick ended (run 673's Sodium client on 26.2 reported
 * neither way). A client still in its world as it stops drops it in {@code Minecraft.destroy()}, after
 * {@code CLIENT_STOPPING}: there the check passes once the last of its block entities unloads, and fails as missing
 * otherwise (run 683's Sodium client on 26.1.2 reported neither way, logging nothing about its world).
 */
public final class BlockEntityEventsClientTest implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Render thread only.
		Set<BlockEntity> loaded = Collections.newSetFromMap(new IdentityHashMap<>());
		int[] loads = {0};
		boolean[] reported = {false};
		boolean[] stoppingInWorld = {false};
		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
			loaded.add(blockEntity);
			loads[0]++;
		});
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> {
			loaded.remove(blockEntity);
			// The stopping client drops its level: the last of its block entities unloaded.
			if (stoppingInWorld[0] && !reported[0] && loaded.isEmpty()) {
				reported[0] = true;
				TestMod.LOGGER.info("FABRICATED-TEST client block entity events OK ({} loaded and unloaded, seen as the stopping client dropped its world)", loads[0]);
			}
		});
		// The end of the tick in which the client dropped its level, or the client stopping after it dropped it.
		ClientTickEvents.END_CLIENT_TICK.register(client -> report(client, loaded, loads[0], reported, "tick"));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			report(client, loaded, loads[0], reported, "stopping");
			if (reported[0]) return;
			// Still in its world, or nothing loaded: say which, for the log.
			stoppingInWorld[0] = client.level != null && loads[0] > 0;
			TestMod.LOGGER.info("FABRICATED-TEST client block entity events: the client stops {}, {} loaded, {} not unloaded yet",
					client.level != null ? "in its world" : "out of its world", loads[0], loaded.size());
		});
	}

	private static void report(Minecraft client, Set<BlockEntity> loaded, int loads, boolean[] reported, String when) {
		if (reported[0] || client.level != null || loads == 0) return;
		reported[0] = true;
		if (loaded.isEmpty()) {
			TestMod.LOGGER.info("FABRICATED-TEST client block entity events OK ({} loaded and unloaded, seen at the client's {})", loads, when);
		} else {
			TestMod.LOGGER.error("FABRICATED-TEST client block entity events FAILED: {} loaded, {} never unloaded", loads, loaded.size());
		}
	}
}
