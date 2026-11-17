package io.github.cmdralph.fabricated.forge;

import net.minecraft.core.Registry;

/**
 * Runs the built-in registries' freeze ({@code Registry.freezeBuiltins}) after Fabric mods' main entrypoints, as on
 * Fabric, where Fabric API moves vanilla's bootstrap freeze to when the server starts (CORPUS-32): Fabric mods mix into
 * it there. Forge 43 freezes the registries during the bootstrap, before any mod is constructed, and again once mods have
 * loaded ({@code GameData.freezeData}).
 *
 * <p>While the main entrypoints are still to run, the bootstrap freezes each registry as {@code freezeBuiltins} does,
 * without running it, so Forge finds them frozen as it expects. {@code freezeBuiltins} runs as Forge's freeze after the
 * entrypoints starts, over the registries Forge opened for its registration events, and Forge's freeze then takes them
 * frozen. On 1.19.2 neither freezes the root of the registries.
 */
public final class BuiltInRegistriesFreeze {
	/** Set while the bootstrap's {@code freezeBuiltins} waits for the main entrypoints. */
	private static volatile boolean deferred;

	private BuiltInRegistriesFreeze() {
	}

	/**
	 * Called for vanilla's bootstrap's {@code freezeBuiltins}: while the main entrypoints are still to run, freezes the
	 * registries as it does, without running it, and returns true; otherwise returns false, for vanilla's.
	 */
	public static boolean deferBootstrapFreeze() {
		if (!MainEntrypoints.pending()) return false;
		for (Registry<?> registry : Registry.REGISTRY) registry.freeze();
		deferred = true;
		return true;
	}

	/** Called as Forge's freeze starts: runs {@code freezeBuiltins} if it waits and the main entrypoints have run. */
	public static void beforeFreezeData() {
		if (!deferred || MainEntrypoints.pending()) return;
		deferred = false;
		Registry.freezeBuiltins();
	}
}
