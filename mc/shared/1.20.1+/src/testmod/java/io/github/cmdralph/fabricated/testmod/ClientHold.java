package io.github.cmdralph.fabricated.testmod;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Keeps a CI client in its world until its integrated server has been there as long as the client. CI's client runs
 * start the game with mc-runtime-test (ci/smoke-client.sh), which quits once the client's player has ticked
 * {@value #WORLD_TICKS} times, whatever the server did meanwhile. The test mods' client checks wait on the server's
 * ticks after the player joined: the renderer test mod's falling and breaking blocks, renewed every 20 ticks, a falling
 * block's glowing flag, which reaches the client with the server's next tick after the block spawned, and the menu
 * and payload round trips the client asks for after 40 of its ticks. A server that falls more than two seconds behind
 * skips the ticks it missed: one ran 78 ticks behind, and its client quit before the glowing falling block's outline
 * was drawn (CI-15).
 *
 * <p>While {@link #holding}, mc-runtime-test's tick hook is skipped ({@code mixin/client/McRuntimeTestTickHookMixin}),
 * so it neither tests nor quits: from the tick its count is up until the server has run {@value #WORLD_TICKS} ticks
 * with a player in its world, for {@value #MAX_HOLD_SECONDS} seconds at most. A server that kept up is there first and
 * nothing waits; a check that never logs still fails, at most that much later. Logs whether it held, and how it ended.
 */
public final class ClientHold implements ClientModInitializer {
	/** mc-runtime-test's count: it tests and quits once the client's player has ticked this many times (4.5.2). */
	static final int WORLD_TICKS = 100;
	/** The longest hold: well within the client runs' time limit (SMOKE_TIMEOUT, 7 minutes or more). */
	static final int MAX_HOLD_SECONDS = 60;
	/** Ticks the integrated server ended with a player in its world (server thread). */
	private static final AtomicInteger SERVER_TICKS = new AtomicInteger();
	/** Whether the server's ticks are counted: without, nothing is held. */
	private static volatile boolean counting;
	// Render thread only.
	private static long heldSince;
	private static int heldTicks;
	private static boolean done;

	@Override
	public void onInitializeClient() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getPlayerCount() > 0) SERVER_TICKS.incrementAndGet();
		});
		counting = true;
	}

	/**
	 * At the start of each client tick, from mc-runtime-test's tick hook: whether it is to skip that tick. Only once its
	 * own count is up (the player's ticks in a singleplayer world), and once per game.
	 */
	public static boolean holding() {
		if (done || !counting) return false;
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.getSingleplayerServer() == null || player.tickCount < WORLD_TICKS) return false;
		int ticks = SERVER_TICKS.get();
		if (heldTicks == 0) {
			if (ticks >= WORLD_TICKS) {
				done = true;
				TestMod.LOGGER.info("FABRICATED-TEST client hold not needed (the integrated server had run {} ticks with the player as the client's {} were up)",
						ticks, WORLD_TICKS);
				return false;
			}
			heldSince = System.nanoTime();
			TestMod.LOGGER.info("FABRICATED-TEST client hold: the client's {} ticks are up, its integrated server has run {} with the player; mc-runtime-test waits"
					+ " until it has run {}, {} seconds at most", WORLD_TICKS, ticks, WORLD_TICKS, MAX_HOLD_SECONDS);
		}
		long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - heldSince);
		if (ticks >= WORLD_TICKS) {
			done = true;
			TestMod.LOGGER.info("FABRICATED-TEST client hold released after {} ms ({} client ticks): the integrated server has run {} ticks with the player",
					millis, heldTicks, ticks);
			return false;
		}
		if (millis >= TimeUnit.SECONDS.toMillis(MAX_HOLD_SECONDS)) {
			done = true;
			TestMod.LOGGER.info("FABRICATED-TEST client hold gave up after {} seconds ({} client ticks): the integrated server ran {} of {} ticks with the player",
					MAX_HOLD_SECONDS, heldTicks, ticks, WORLD_TICKS);
			return false;
		}
		heldTicks++;
		return true;
	}
}
