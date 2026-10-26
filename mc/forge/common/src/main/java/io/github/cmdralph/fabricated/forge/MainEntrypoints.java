package io.github.cmdralph.fabricated.forge;

import io.github.cmdralph.fabricated.loader.FabricatedState;

/**
 * Whether Fabric mods' main entrypoints are still to run: from the game's start, through vanilla's bootstrap, which
 * freezes the registries before any mod is constructed (CORPUS-31), to the registration event {@link FabricatedMod}
 * runs them in. On Fabric, Fabric API leaves the registries open until then.
 */
public final class MainEntrypoints {
	private static volatile boolean ran;

	private MainEntrypoints() {
	}

	public static boolean pending() {
		return !ran && FabricatedState.get() != null;
	}

	static void markRan() {
		ran = true;
	}
}
