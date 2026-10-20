package io.github.cmdralph.fabricated.testmod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.server.packs.resources.ReloadInstance;

/**
 * CI diagnostics for a world that never finishes loading (mc-runtime-test then waits for the player's chunk): once the
 * player has been in the world for {@value #DELAY_TICKS} client ticks, logs the client's overlay and pause state and
 * whether chunks arrived. In singleplayer the integrated server stops while the client is paused, which an overlay (a
 * resource reload's, say) does with no screen open. The overlay, pause state and chunk count are looked up by name (26.2
 * moved the overlay from {@code Minecraft} to its {@code gui}), and are null where a version has no such method.
 */
final class WorldStateReport {
	private static final int DELAY_TICKS = 600;

	private static int ticksInWorld;

	private WorldStateReport() {
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) {
				ticksInWorld = 0;
				return;
			}
			if (++ticksInWorld != DELAY_TICKS) return;
			int chunkX = SectionPos.blockToSectionCoord(client.player.getBlockX());
			int chunkZ = SectionPos.blockToSectionCoord(client.player.getBlockZ());
			Object overlay = call(client, "getOverlay");
			if (overlay == null) overlay = call(field(client, "gui"), "overlay");
			TestMod.LOGGER.info("FABRICATED-TEST world state after {} ticks: overlay {}{}, paused {}, player's chunk {} {} {}, chunks loaded {}",
					DELAY_TICKS, overlay == null ? "none" : overlay.getClass().getName(), reloadOf(overlay), call(client, "isPaused"),
					chunkX, chunkZ, client.level.getChunk(chunkX, chunkZ).isEmpty() ? "empty" : "loaded",
					call(client.level.getChunkSource(), "getLoadedChunksCount"));
		});
	}

	/** The result of a public no-argument method, or null when there is none. */
	private static Object call(Object target, String name) {
		if (target == null) return null;
		try {
			Method method = target.getClass().getMethod(name);
			return method.invoke(target);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	private static Object field(Minecraft client, String name) {
		try {
			return Minecraft.class.getField(name).get(client);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** The state of the resource reload an overlay shows (a loading overlay's private field), or nothing. */
	private static String reloadOf(Object overlay) {
		for (Class<?> type = overlay == null ? null : overlay.getClass(); type != null; type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (!ReloadInstance.class.isAssignableFrom(field.getType())) continue;
				try {
					field.setAccessible(true);
					ReloadInstance reload = (ReloadInstance) field.get(overlay);
					return " (reload done " + reload.isDone() + ", progress " + reload.getActualProgress() + ")";
				} catch (ReflectiveOperationException | RuntimeException e) {
					return " (reload unreadable: " + e + ")";
				}
			}
		}
		return "";
	}
}
