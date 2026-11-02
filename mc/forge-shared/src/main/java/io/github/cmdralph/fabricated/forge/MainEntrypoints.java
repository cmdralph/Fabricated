package io.github.cmdralph.fabricated.forge;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.loader.FabricatedState;

/**
 * Whether Fabric mods' main entrypoints are still to run: from the game's start, through vanilla's bootstrap, which
 * freezes the registries before any mod is constructed (CORPUS-31), to the registration event {@link FabricatedMod}
 * runs them in. On Fabric, Fabric API leaves the registries open until then.
 */
public final class MainEntrypoints {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static volatile boolean ran;
	private static volatile boolean failed;

	private MainEntrypoints() {
	}

	public static boolean pending() {
		return !ran && FabricatedState.get() != null;
	}

	static void markRan() {
		ran = true;
	}

	/** A Fabric mod's entrypoint failed (any phase); called before the registries freeze after the entrypoints. */
	static void markFailed() {
		failed = true;
	}

	/**
	 * Called as a registry freezes after the main entrypoints, with the intrusive holders nothing registered: whether to
	 * drop them, so the freeze passes. Only once a Fabric mod's entrypoint has failed (CORPUS-42, HOLD-2): content that
	 * mod built before failing is never registered, and vanilla's check would stop the game for a mod already marked as
	 * failed. Otherwise vanilla's check stands, as on Fabric. The report is written again with the drop: it was written
	 * as the entrypoints ended (see EntrypointOutcome), before this freeze, and Forge has no later write.
	 */
	public static boolean dropLeftoverHolders(Object registry, Collection<?> holders) {
		if (!failed) return false;
		String text = registry + " had " + holders.size() + " entr" + (holders.size() == 1 ? "y" : "ies")
				+ " built but never registered, left by a Fabric mod whose entrypoint failed; dropped so the game can start: " + holders;
		LOGGER.error("{}", text);
		FabricatedState state = FabricatedState.get();
		if (state != null) {
			state.report().line("Entrypoint failures", text);
			state.writeReport();
		}
		return true;
	}
}
