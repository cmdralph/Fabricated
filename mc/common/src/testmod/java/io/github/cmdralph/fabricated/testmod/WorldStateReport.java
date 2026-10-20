package io.github.cmdralph.fabricated.testmod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.SectionPos;

/**
 * CI diagnostics for a world that never finishes loading (mc-runtime-test then waits for the player's chunk): once the
 * player has been in the world for {@value #DELAY_TICKS} client ticks, logs what the client shows and whether chunks
 * arrived. In singleplayer the integrated server stops while the client is paused, which a screen or an overlay (a
 * resource reload's, say) does.
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
			TestMod.LOGGER.info("FABRICATED-TEST world state after {} ticks: screen {}, overlay {}, paused {}, player's chunk {} {} {}, {}",
					DELAY_TICKS, className(client.screen), className(client.getOverlay()), client.isPaused(), chunkX, chunkZ,
					client.level.getChunk(chunkX, chunkZ).isEmpty() ? "empty" : "loaded", client.level.getChunkSource().gatherStats());
		});
	}

	private static String className(Object object) {
		return object == null ? "none" : object.getClass().getName();
	}
}
