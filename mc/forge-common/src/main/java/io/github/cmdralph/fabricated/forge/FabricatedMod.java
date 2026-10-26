package io.github.cmdralph.fabricated.forge;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.runtime.EntrypointRunner;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLLoader;

/**
 * Fabricated's in-game mod on Minecraft Forge, shared by every project of the forge branch. It runs the Fabric mods'
 * entrypoints once discovery has handed Forge their files (docs/PORTING.md §4.1, F3 step 3): for now the pre-launch, main
 * and dedicated server ones as the mod is constructed. Where Fabric runs them (the main ones as registries open, the
 * client ones in Minecraft's constructor) and Fabric API on Forge's events follow.
 */
@Mod(FabricatedMod.MOD_ID)
public final class FabricatedMod {
	public static final String MOD_ID = "fabricated";
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	public FabricatedMod() {
		FabricatedState state = FabricatedState.get();
		if (state == null) {
			LOGGER.warn("Fabricated's discovery did not run; Fabric mods are not loaded");
			return;
		}
		EntrypointRunner runner = new EntrypointRunner(state.runtime(), new EntrypointRunner.ContextHook() {
			@Override
			public void enter(String modId) {
			}

			@Override
			public void exit() {
			}
		});
		List<EntrypointRunner.Failure> failures = new ArrayList<>();
		failures.addAll(runner.run("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch));
		failures.addAll(runner.run("main", ModInitializer.class, ModInitializer::onInitialize));
		if (FMLLoader.getDist().isDedicatedServer()) {
			failures.addAll(runner.run("server", DedicatedServerModInitializer.class, DedicatedServerModInitializer::onInitializeServer));
		} else {
			LOGGER.warn("Fabric client entrypoints don't run on Forge yet");
		}
		for (EntrypointRunner.Failure failure : failures) LOGGER.error("Fabric entrypoint failed: {}", failure.describe(), failure.error());
		LOGGER.info("Fabricated ran the Fabric entrypoints of {} mod(s) on Forge ({} failed)", state.mods().size(), failures.size());
	}
}
