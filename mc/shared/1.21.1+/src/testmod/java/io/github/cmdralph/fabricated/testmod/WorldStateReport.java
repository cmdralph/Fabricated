package io.github.cmdralph.fabricated.testmod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ReloadInstance;

/**
 * CI diagnostics for a world that never finishes loading (mc-runtime-test then waits for the player's chunk): once the
 * player has been in the world for {@value #DELAY_TICKS} client ticks, logs the client's overlay and pause state,
 * whether chunks and packets arrive, and, from the integrated server's thread, whether the server ticks and has the
 * player's chunk. Members that differ between versions are looked up by name (26.2 moved the overlay from
 * {@code Minecraft} to its {@code gui}), and are null where a version has no such method.
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
			Object connection = call(call(client, "getConnection"), "getConnection");
			TestMod.LOGGER.info("FABRICATED-TEST world state after {} ticks: overlay {}{}, paused {}, player's chunk {} {} {}, chunks loaded {}, packets received per tick {}",
					DELAY_TICKS, overlay == null ? "none" : overlay.getClass().getName(), reloadOf(overlay), call(client, "isPaused"),
					chunkX, chunkZ, client.level.getChunk(chunkX, chunkZ).isEmpty() ? "empty" : "loaded",
					call(client.level.getChunkSource(), "getLoadedChunksCount"), call(connection, "getAverageReceivedPackets"));
			// The integrated server's side, on its own thread: no line means that thread never got to it.
			MinecraftServer server = client.getSingleplayerServer();
			if (server != null) server.execute(() -> reportServer(server, chunkX, chunkZ));
		});
	}

	private static void reportServer(MinecraftServer server, int chunkX, int chunkZ) {
		Object players = call(server.getPlayerList(), "getPlayers");
		Object player = players instanceof List<?> list && !list.isEmpty() ? list.getFirst() : null;
		Object chunks = call(call(player, "level"), "getChunkSource");
		TestMod.LOGGER.info("FABRICATED-TEST server state: tick {}, paused {}, players {} ({} counted, a {}) in the list {} of server {}, player's chunk {} {} on the server {}, server chunks loaded {}",
				call(server, "getTickCount"), call(server, "isPaused"), players instanceof List<?> list ? list.size() : players,
				call(server.getPlayerList(), "getPlayerCount"), players == null ? null : players.getClass().getName(),
				System.identityHashCode(server.getPlayerList()), System.identityHashCode(server), chunkX, chunkZ,
				call(chunks, "hasChunk", chunkX, chunkZ), call(chunks, "getLoadedChunksCount"));
	}

	/** The result of a public method taking these arguments, or null when there is none. */
	private static Object call(Object target, String name, Object... args) {
		if (target == null) return null;
		for (Method method : target.getClass().getMethods()) {
			if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
			try {
				return method.invoke(target, args);
			} catch (ReflectiveOperationException | RuntimeException e) {
				return e;
			}
		}
		return null;
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
