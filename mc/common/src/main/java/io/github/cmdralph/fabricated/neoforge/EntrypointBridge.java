package io.github.cmdralph.fabricated.neoforge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.config.FabricatedConfig;
import io.github.cmdralph.fabricated.core.runtime.EntrypointRunner;
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.ModLoadingIssue;

/**
 * Runs Fabric's built-in entrypoints once, in Fabric dependency order: {@code preLaunch}, then all
 * {@code main}, then all {@code client} (client) or {@code server} (dedicated server), matching Fabric
 * Loader's ordering. While a mod's entrypoint runs, NeoForge's mod loading context points at that mod
 * so NeoForge attributes registrations and errors correctly.
 */
final class EntrypointBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String MESSAGE = "§e[Fabricated]§r {0}";

	private final FabricatedState state;
	private final Dist dist;
	private boolean ran;
	private EntrypointRunner runner;

	EntrypointBridge(FabricatedState state, Dist dist) {
		this.state = state;
		this.dist = dist;
	}

	synchronized void runOnce() {
		if (ran) return;
		ran = true;
		long start = System.nanoTime();
		if (dist.isClient()) state.runtime().setGameInstance(ClientSupport.gameInstance());

		runner = new EntrypointRunner(state.runtime(), new EntrypointRunner.ContextHook() {
			@Override
			public void enter(String modId) {
				container(modId).ifPresent(c -> ModLoadingContext.get().setActiveContainer(c));
			}

			@Override
			public void exit() {
				ModLoadingContext.get().setActiveContainer(null);
			}
		});

		List<EntrypointRunner.Failure> failures = new ArrayList<>();
		failures.addAll(runner.run("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch));
		failures.addAll(runner.run("main", ModInitializer.class, ModInitializer::onInitialize));
		if (dist.isClient()) {
			failures.addAll(runner.run("client", ClientModInitializer.class, ClientModInitializer::onInitializeClient));
		} else {
			failures.addAll(runner.run("server", DedicatedServerModInitializer.class, DedicatedServerModInitializer::onInitializeServer));
		}

		FabricatedConfig.EntrypointFailure policy = state.config().onEntrypointFailure();
		for (EntrypointRunner.Failure failure : failures) {
			String text = "Fabric mod " + failure.describe() + ". "
					+ (policy == FabricatedConfig.EntrypointFailure.ERROR
							? "Fix: update the mod, or disable it with \"mods\": {\"" + failure.modId() + "\": {\"disabled\": true}} in config/fabricated.json."
							: "The mod was marked as failed and the game continues; parts of it may not work.");
			LOGGER.error("{}", text, failure.error());
			state.report().line("Entrypoint failures", text);
			ModLoadingIssue issue = (policy == FabricatedConfig.EntrypointFailure.ERROR
					? ModLoadingIssue.error(MESSAGE, text)
					: ModLoadingIssue.warning(MESSAGE, text)).withCause(failure.error());
			Optional<? extends ModContainer> container = container(failure.modId());
			if (container.isPresent()) issue = issue.withAffectedMod(container.get().getModInfo());
			ModLoader.addLoadingIssue(issue);
		}
		LOGGER.info("Fabricated ran Fabric entrypoints for {} mod(s) in {} ms ({} failure(s))",
				state.mods().size(), (System.nanoTime() - start) / 1_000_000, failures.size());
	}

	/** Records timings and writes the final report once loading has completed. */
	synchronized void complete() {
		if (runner != null) {
			for (Map.Entry<String, Long> e : runner.timings().entrySet()) {
				if (e.getValue() >= 50) state.report().line("Slow entrypoints (50 ms or more)", e.getKey() + ": " + e.getValue() + " ms");
			}
		}
		state.writeReport();
	}

	private static Optional<? extends ModContainer> container(String fabricModId) {
		return ModList.get().getModContainerById(ModIds.toNeoForgeId(fabricModId));
	}
}
