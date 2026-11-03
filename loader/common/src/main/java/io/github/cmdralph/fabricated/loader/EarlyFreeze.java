package io.github.cmdralph.fabricated.loader;

/**
 * Whether a registry freeze now comes before Fabric mods' main entrypoints (REG-8). On Fabric no freeze comes there:
 * Fabric API moves vanilla's bootstrap freeze after the main entrypoints (on a client, after the client entrypoints
 * too). NeoForge freezes the registries several times before them: at bootstrap (where the in-game mod freezes them as
 * vanilla's freeze does, {@code BuiltInRegistriesFreeze}), the root after its new registry event and after a write to
 * it from vanilla's code (REG-3), and on a client all of them again before the game's client exists, whose constructor
 * runs the main entrypoints. The handlers Fabric mods' {@code @Inject}s add to {@code MappedRegistry.freeze} return at
 * once while {@link #beforeMainEntrypoints}: pre-flight gives each such handler that check ({@code MixinRepairs}).
 * Flashback's starts Flashback from there, whose start-up registers key bindings through the game's client, which
 * doesn't exist yet.
 *
 * <p>The platform's discovery holds the handlers once it knows Fabric entrypoints will run, and its in-game mod lets
 * them go once the main entrypoints have run; on a platform that does neither, they run in every freeze.
 */
public final class EarlyFreeze {
	/** Set from discovery until the main entrypoints have run. */
	private static volatile boolean held;

	private EarlyFreeze() {
	}

	/** Called by the platform's discovery when Fabric entrypoints will run: freezes from now on are early ones. */
	public static void holdUntilMainEntrypoints() {
		held = true;
	}

	/** Called by the platform's in-game mod once Fabric mods' main entrypoints have run. */
	public static void mainEntrypointsRan() {
		held = false;
	}

	/** Whether Fabric mods' main entrypoints are still to run: a Fabric mod's freeze handler returns at once then (called from its code). */
	public static boolean beforeMainEntrypoints() {
		return held;
	}
}
