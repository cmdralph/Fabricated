package io.github.cmdralph.fabricated.forge;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.config.FabricatedConfig;
import io.github.cmdralph.fabricated.core.runtime.EntrypointRunner;
import io.github.cmdralph.fabricated.loader.FabricatedState;

/**
 * What Forge's in-game mod does once the Fabric initializers have run, on Forge 47 to 66 alike, as NeoForge's does
 * ({@code EntrypointBridge.finish}): each failure goes to the report and to Forge's loading warnings (the client shows
 * them on its warnings screen, the server logs them), then Fabricated's API runs what waited for the initializers
 * ({@code Fabricated.get().afterEntrypoints}), the failed mods known. Forge's loading goes on after a failure whichever
 * {@code onEntrypointFailure} is set: stopping it there, as NeoForge's error screen does with {@code error}, isn't
 * supported on Forge.
 */
final class EntrypointOutcome {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	private EntrypointOutcome() {
	}

	/** @param warning adds a text to Forge's loading warnings */
	static void complete(FabricatedState state, List<EntrypointRunner.Failure> failures, Consumer<String> warning) {
		boolean error = state.config().onEntrypointFailure() == FabricatedConfig.EntrypointFailure.ERROR;
		Set<String> failed = new LinkedHashSet<>();
		for (EntrypointRunner.Failure failure : failures) {
			failed.add(failure.modId());
			String text = "Fabric mod " + failure.describe() + ". " + (error
					? "Fix: update the mod, or disable it with \"mods\": {\"" + failure.modId() + "\": {\"disabled\": true}} in config/fabricated.json."
							+ " Forge's loading went on; parts of the mod may not work."
					: "The mod was marked as failed and the game continues; parts of it may not work.");
			state.report().line("Entrypoint failures", text);
			warning.accept(text);
		}
		state.api().markEntrypointsComplete(failed, (action, e) -> {
			String text = "An action waiting for Fabric mods' initializers (" + action.getClass().getName() + ") threw " + e;
			LOGGER.error("{}", text, e);
			warning.accept(text);
		});
		// Rewritten when loading completes; written now too, in case the game stops before that.
		if (!failures.isEmpty()) state.writeReport();
	}
}
