package io.github.cmdralph.fabricated.neoforge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.config.FabricatedConfig;
import io.github.cmdralph.fabricated.core.runtime.EntrypointRunner;
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import io.github.cmdralph.fabricated.neoforge.registry.FabricTableEntries;
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
import net.neoforged.neoforge.registries.GameData;

/**
 * Runs Fabric's built-in entrypoints once, in Fabric dependency order: {@code preLaunch}, then all
 * {@code main}, then all {@code client} (client) or {@code server} (dedicated server), matching Fabric
 * Loader's ordering. While a mod's entrypoint runs, NeoForge's mod loading context points at that mod
 * so NeoForge attributes registrations and errors correctly.
 *
 * <p>NeoForge builds and registers mods before it creates {@code Minecraft}, while Fabric Loader runs every
 * initializer from {@code Minecraft}'s constructor, once {@code Minecraft.getInstance()} is set. Fabric content must
 * be registered while the registries are open, so {@code preLaunch} and {@code main} run in the RegisterEvent loop;
 * {@code client} runs where Fabric runs it ({@link #runClientEntrypoints()}), since client initializers commonly use
 * the instance, with the registries opened again for it (Fabric API freezes them only after it).
 */
public final class EntrypointBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String MESSAGE = "§e[Fabricated]§r {0}";
	/** The bridge whose client entrypoints are still to run. */
	private static volatile EntrypointBridge pendingClient;

	private final FabricatedState state;
	private final Dist dist;
	private boolean ran;
	private EntrypointRunner runner;
	private final List<EntrypointRunner.Failure> failures = new ArrayList<>();
	private long nanos;

	EntrypointBridge(FabricatedState state, Dist dist) {
		this.state = state;
		this.dist = dist;
	}

	synchronized void runOnce() {
		if (ran) return;
		ran = true;
		long start = System.nanoTime();

		runner = new EntrypointRunner(state.runtime(), new EntrypointRunner.ContextHook() {
			// Restored after each entrypoint: Fabricated's own in the RegisterEvent loop, none in Minecraft's constructor.
			private ModContainer previous;

			@Override
			public void enter(String modId) {
				previous = ModLoadingContext.get().getActiveContainer();
				container(modId).ifPresent(c -> ModLoadingContext.get().setActiveContainer(c));
			}

			@Override
			public void exit() {
				ModLoadingContext.get().setActiveContainer(previous);
			}
		});

		FabricTableEntries.recordVanilla();
		failures.addAll(runner.run("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch));
		failures.addAll(runner.run("main", ModInitializer.class, ModInitializer::onInitialize));
		if (dist.isClient()) {
			nanos = System.nanoTime() - start;
			pendingClient = this;
			return;
		}
		failures.addAll(runner.run("server", DedicatedServerModInitializer.class, DedicatedServerModInitializer::onInitializeServer));
		nanos = System.nanoTime() - start;
		finish();
	}

	/** Runs the client entrypoints (once); called from {@code Minecraft}'s constructor where Fabric Loader runs them. */
	public static void runClientEntrypoints() {
		EntrypointBridge bridge = pendingClient;
		pendingClient = null;
		if (bridge != null) bridge.runClient();
	}

	private synchronized void runClient() {
		long start = System.nanoTime();
		state.runtime().setGameInstance(ClientSupport.gameInstance());
		// Fabric API freezes the registries after the client initializers too, and some register content there
		// (Xaero's Minimap its effect). NeoForge froze them before Minecraft existed: they are opened for these
		// initializers and frozen again as NeoForge freezes them, which renews the snapshot it restores on leaving a
		// server.
		GameData.unfreezeData();
		try {
			failures.addAll(runner.run("client", ClientModInitializer.class, ClientModInitializer::onInitializeClient));
		} finally {
			GameData.freezeData();
		}
		nanos += System.nanoTime() - start;
		finish();
	}

	private void finish() {
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
				state.mods().size(), nanos / 1_000_000, failures.size());
		Set<String> failed = new HashSet<>();
		for (EntrypointRunner.Failure failure : failures) failed.add(failure.modId());
		state.api().markEntrypointsComplete(failed, (action, error) -> {
			String text = "An action waiting for Fabric mods' initializers (" + action.getClass().getName() + ") threw " + error;
			LOGGER.error("{}", text, error);
			ModLoader.addLoadingIssue(ModLoadingIssue.warning(MESSAGE, text).withCause(error));
		});
		// Rewritten when loading completes; written now too, in case the game stops before that.
		if (!failures.isEmpty()) state.writeReport();
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
