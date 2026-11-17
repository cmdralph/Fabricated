package io.github.cmdralph.fabricated.testmod;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Client half of {@link BlockEntityEventsTest}: once the client has left its world (mc-runtime-test disconnects
 * before quitting), every block entity it loaded there, the chest behind the player at least, has been unloaded.
 * NeoForge posts no unload for the block entities of a level the client drops. Checked at the end of the tick the
 * client left its world in, or as it stops, for a client that quit before that tick ended (run 673's Sodium client on
 * 26.2 reported neither way). A client still in its world as it stops drops it in {@code Minecraft.destroy()}, after
 * {@code CLIENT_STOPPING}: there the check passes once the last of its block entities unloads, and fails as missing
 * otherwise (run 683's Sodium client on 26.1.2 reported neither way, logging nothing about its world).
 *
 * <p>Only the client's own world counts: a mod's level of its own (PolyPlus 2 draws its title screen's scene from
 * one, with block entities, before the client joins any world) loads block entities the client never leaves a world
 * for. Those are logged, not judged.
 */
public final class BlockEntityEventsClientTest implements ClientModInitializer {
	/** The block entities loaded and not unloaded yet, and how many were loaded, by the level they were loaded in. */
	private final Map<Object, Set<BlockEntity>> loaded = new IdentityHashMap<>();
	private final Map<Object, Integer> loads = new IdentityHashMap<>();
	/** The world the client is in, or was last in. */
	private Object world;
	private boolean reported;
	private boolean stoppingInWorld;

	@Override
	public void onInitializeClient() {
		// Render thread only.
		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
			loaded.computeIfAbsent(level, key -> Collections.newSetFromMap(new IdentityHashMap<>())).add(blockEntity);
			loads.merge(level, 1, Integer::sum);
		});
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> {
			Set<BlockEntity> ofLevel = loaded.get(level);
			if (ofLevel != null) ofLevel.remove(blockEntity);
			// The stopping client drops its level: the last of its block entities unloaded.
			if (stoppingInWorld && !reported && level == world && stillLoaded() == 0) {
				reported = true;
				TestMod.LOGGER.info("FABRICATED-TEST client block entity events OK ({} loaded and unloaded, seen as the stopping client dropped its world)", loadsInWorld());
			}
		});
		// The end of the tick in which the client dropped its level, or the client stopping after it dropped it.
		ClientTickEvents.END_CLIENT_TICK.register(client -> report(client, "tick"));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			report(client, "stopping");
			if (reported) return;
			// Still in its world, or nothing loaded: say which, for the log.
			stoppingInWorld = client.level != null && loadsInWorld() > 0;
			TestMod.LOGGER.info("FABRICATED-TEST client block entity events: the client stops {}, {} loaded, {} not unloaded yet",
					client.level != null ? "in its world" : "out of its world", loadsInWorld(), stillLoaded());
		});
	}

	private int loadsInWorld() {
		return world == null ? 0 : loads.getOrDefault(world, 0);
	}

	private int stillLoaded() {
		Set<BlockEntity> ofWorld = world == null ? null : loaded.get(world);
		return ofWorld == null ? 0 : ofWorld.size();
	}

	private void report(Minecraft client, String when) {
		if (client.level != null) world = client.level;
		if (reported || client.level != null || loadsInWorld() == 0) return;
		reported = true;
		int elsewhere = loads.entrySet().stream().filter(entry -> entry.getKey() != world).mapToInt(Map.Entry::getValue).sum();
		if (elsewhere > 0) {
			TestMod.LOGGER.info("FABRICATED-TEST client block entity events: {} more loaded in levels that were never the client's world, not judged", elsewhere);
		}
		if (stillLoaded() == 0) {
			TestMod.LOGGER.info("FABRICATED-TEST client block entity events OK ({} loaded and unloaded, seen at the client's {})", loadsInWorld(), when);
		} else {
			TestMod.LOGGER.error("FABRICATED-TEST client block entity events FAILED: {} loaded, {} never unloaded", loadsInWorld(), stillLoaded());
		}
	}
}
