package io.github.cmdralph.fabricated.loader;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Fabric mod's handler that threw in code the platform runs before Fabric mods' entrypoints, where Fabric runs it after
 * them. NeoForge 21.1's and Forge's clients create the game's options before they load mods, and so before the
 * entrypoints, which on Fabric run before the options exist: a handler there reading what its mod's initializer sets up
 * (Bobby's config) threw, and the game stopped (ENTRY-2). Forge loads mods part way through the client's constructor,
 * whose start runs the entrypoints on Fabric: Sodium's hook there reads its config (ENTRY-3). Pre-flight wraps each Fabric
 * mod's handler into the options, and into the client's constructor ({@code MixinRepairs}), to call {@link #failed} with
 * what it threw, and to go on as if its hook weren't there when that returns.
 */
public final class EarlyHandlers {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

	private EarlyHandlers() {
	}

	/**
	 * Called from a wrapped handler (by its mixin's name and its own) with what it threw: returns, so the handler is
	 * skipped for that call, while Fabric mods' main entrypoints are still to run ({@link EarlyFreeze}), with a warning
	 * and a line in the report the first time; throws {@code thrown} otherwise, as the handler would have.
	 */
	public static void failed(RuntimeException thrown, String handler) {
		if (!EarlyFreeze.beforeMainEntrypoints()) throw thrown;
		if (!REPORTED.add(handler)) return;
		String text = handler + " hooks into code this platform runs before Fabric mods' entrypoints (the game's options, or the"
				+ " client's constructor before it loads mods), where Fabric runs it after them, and threw " + thrown + " before they ran;"
				+ " it was skipped for that call, so what it changes there stays as the game has it";
		LOGGER.warn("{}", text, thrown);
		FabricatedState state = FabricatedState.get();
		if (state != null) state.report().line("Hooks skipped before Fabric mods' entrypoints", text);
	}
}
