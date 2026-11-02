package io.github.cmdralph.fabricated.loader;

/**
 * A registry freeze the platform runs before Fabric mods' main entrypoints (REG-8). On Fabric no freeze comes there:
 * Fabric API moves vanilla's bootstrap freeze after the main entrypoints (on a client, after the client entrypoints
 * too). NeoForge and Forge freeze the built-in registries at bootstrap, before any mod is constructed, where the in-game
 * mod freezes them as vanilla's freeze does ({@code BuiltInRegistriesFreeze}), and NeoForge freezes them again on a
 * client before the game's client exists, whose constructor runs the main entrypoints. The in-game mod runs those
 * freezes inside {@link #run}, and the handlers Fabric mods' {@code @Inject}s add to {@code MappedRegistry.freeze}
 * return at once while {@link #running}: pre-flight gives each such handler that check ({@code MixinRepairs}).
 * Flashback's starts Flashback from there, whose start-up registers key bindings through the game's client, which
 * doesn't exist yet.
 *
 * <p>Only on the thread running such a freeze; any other freeze, and every other thread, runs the handlers.
 */
public final class EarlyFreeze {
	/** The thread in {@link #run}, or null. */
	private static volatile Thread freezing;

	private EarlyFreeze() {
	}

	/** Runs a freeze of the platform's before the main entrypoints, with Fabric mods' freeze handlers standing by. */
	public static void run(Runnable freeze) {
		Thread previous = freezing;
		freezing = Thread.currentThread();
		try {
			freeze.run();
		} finally {
			freezing = previous;
		}
	}

	/** Whether this thread is in {@link #run}: a Fabric mod's freeze handler returns at once then (called from its code). */
	public static boolean running() {
		return freezing == Thread.currentThread();
	}
}
