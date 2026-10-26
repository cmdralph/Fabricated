package io.github.cmdralph.fabricated.forge;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.runtime.EntrypointRunner;
import io.github.cmdralph.fabricated.forge.fluid.FabricFluidType;
import io.github.cmdralph.fabricated.forge.loot.LootCompat;
import io.github.cmdralph.fabricated.forge.objectbuilder.DefaultAttributeModify;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import io.github.cmdralph.fabricated.loader.ForgeLoaderHooks;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.fml.ModLoadingWarning;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryManager;

/**
 * Fabricated's in-game mod on Minecraft Forge, shared by every project of the forge branch. It runs the Fabric mods'
 * entrypoints once discovery has handed Forge their files (docs/PORTING.md §4.1): the pre-launch ones as the mod is
 * constructed, the main and dedicated server ones as registries open. The client ones (in Minecraft's constructor) and
 * the rest of Fabric API on Forge's events follow (its loot API is native: {@code forge.loot}). It also adds the Fabric
 * mods discovery didn't load to Forge's loading warnings, which Forge reads once every mod has loaded: the server logs
 * them, the client shows them on its warnings screen.
 *
 * <p>Fabric mods register their content directly from their initializers. Forge opens vanilla's registries for its
 * registration events only ({@code UNFREEZE_DATA}, then {@code LOAD_REGISTRIES}), and each registry it manages only for
 * that registry's own event; the others are locked, those whose event hasn't come yet too (found on Forge 47, whose
 * registration Forge 61 to 66 keep: read in their {@code ForgeStatesProvider}, {@code GameData} and {@code ForgeRegistry}).
 * The initializers run in the event after the attribute registry's, as every mod has registered its attributes by then,
 * Forge's own among them, which the attribute builders Forge patches include (a Fabric initializer building an entity's
 * attributes needs them), with every other registry Forge manages for vanilla opened for as long as they run, then locked
 * as Forge leaves a registry after its event; Forge opens each again for its own event.
 */
@Mod(FabricatedMod.MOD_ID)
public final class FabricatedMod {
	public static final String MOD_ID = "fabricated";
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** A warning's message pattern, which Forge formats with the mod, the stage, then the warning's arguments: the first argument as is. */
	private static final String AS_IS = "{2}";

	private boolean attributesRegistered;
	private boolean initialized;

	public FabricatedMod(FMLJavaModLoadingContext context) {
		// Native fabric-loot-api-v3 on Forge's loot table loading (see LootBridge).
		LootCompat.register();
		FabricatedState state = FabricatedState.get();
		if (state == null) {
			LOGGER.warn("Fabricated's discovery did not run; Fabric mods are not loaded");
			return;
		}
		// Fabric API's default attribute event, passed through, in Forge's attribute modification (see DefaultAttributeModify).
		DefaultAttributeModify.register();
		for (String text : ForgeLoaderHooks.notLoaded()) {
			LoadingWarnings.add(new ModLoadingWarning(context.getContainer().getModInfo(), ModLoadingStage.CONSTRUCT, AS_IS, text));
		}
		EntrypointRunner runner = new EntrypointRunner(state.runtime(), new EntrypointRunner.ContextHook() {
			@Override
			public void enter(String modId) {
			}

			@Override
			public void exit() {
			}
		});
		report(state, runner.run("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch), "pre-launch");
		RegisterEvent.getBus(context.getModBusGroup()).addListener(event -> {
			if (initialized) return;
			if (event.getRegistryKey().equals(ForgeRegistries.Keys.ATTRIBUTES)) {
				attributesRegistered = true;
			} else if (attributesRegistered) {
				initialized = true;
				initialize(state, runner, event.getForgeRegistry());
			}
		});
	}

	/** @param current the registry whose event this is, which Forge opened and locks after it (null if Forge doesn't manage it) */
	private static void initialize(FabricatedState state, EntrypointRunner runner, Object current) {
		List<ForgeRegistry<?>> opened = new ArrayList<>();
		for (var key : RegistryManager.getVanillaRegistryKeys()) {
			ForgeRegistry<?> registry = RegistryManager.ACTIVE.getRegistry(key);
			if (registry != null && registry != current) {
				registry.unfreeze();
				opened.add(registry);
			}
		}
		List<EntrypointRunner.Failure> failures = new ArrayList<>();
		try {
			failures.addAll(runner.run("main", ModInitializer.class, ModInitializer::onInitialize));
			if (FMLLoader.getDist().isDedicatedServer()) {
				failures.addAll(runner.run("server", DedicatedServerModInitializer.class, DedicatedServerModInitializer::onInitializeServer));
			} else {
				LOGGER.warn("Fabric client entrypoints don't run on Forge yet");
			}
			// Each Fabric fluid family's Forge fluid type, which Forge asks every fluid for (see FabricFluidType).
			FabricFluidType.registerAll();
		} finally {
			opened.forEach(ForgeRegistry::freeze);
		}
		report(state, failures, "main");
	}

	private static void report(FabricatedState state, List<EntrypointRunner.Failure> failures, String phase) {
		for (EntrypointRunner.Failure failure : failures) LOGGER.error("Fabric entrypoint failed: {}", failure.describe(), failure.error());
		LOGGER.info("Fabricated ran the Fabric {} entrypoints of {} mod(s) on Forge ({} failed)", phase, state.mods().size(), failures.size());
	}
}
