package io.github.cmdralph.fabricated.forgetest;

/**
 * Keeps a client in its world while a check of the test mod's on the integrated server hasn't logged its result. CI's
 * client runs start the game with mc-runtime-test (ci/forge/smoke-client.sh), which quits a few seconds after the
 * player's chunk has loaded: once before the interaction check's armor stand part had run, as the test site's chunk
 * took longer to tick entities than that, so its result was never logged (#254's run 41582983779, Forge 26.2 with
 * Fabric API). The test mod's mixin into mc-runtime-test ({@code mixin/McRuntimeTestMixin}) skips its tick while {@link
 * #holding}, for a minute of client ticks at most. Names no game or Fabric API class, so the client loads it whatever
 * is installed.
 */
public final class ClientHold {
	/** A minute of client ticks: past the 20 seconds of server ticks a check waits, with room for a slow server. */
	private static final int MAX_HOLD = 1200;
	private static volatile boolean pending;
	/** Client ticks held so far (render thread). */
	private static int held;

	private ClientHold() {
	}

	/** On the integrated server: a check has started that logs its result at a later tick. */
	public static void start() {
		pending = true;
	}

	/** On the integrated server: the check has logged its result, or given up. */
	public static void done() {
		pending = false;
	}

	/** On the client, each tick: whether mc-runtime-test is to wait that tick. */
	public static boolean holding() {
		return pending && held++ < MAX_HOLD;
	}
}
