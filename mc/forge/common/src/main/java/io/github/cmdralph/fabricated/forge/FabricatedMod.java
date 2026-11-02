package io.github.cmdralph.fabricated.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.runtime.EntrypointRunner;
import io.github.cmdralph.fabricated.forge.fluid.FabricFluidType;
import io.github.cmdralph.fabricated.forge.loot.LootCompat;
import io.github.cmdralph.fabricated.forge.objectbuilder.DefaultAttributeModify;
import io.github.cmdralph.fabricated.forge.screen.ScreenDrawEvents;
import io.github.cmdralph.fabricated.forge.transfer.TransferBridge;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import io.github.cmdralph.fabricated.loader.ForgeLoaderHooks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.fml.ModLoadingWarning;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryManager;

/**
 * Fabricated's in-game mod on Minecraft Forge, shared by every project of the forge branch. It runs the Fabric mods'
 * entrypoints once discovery has handed Forge their files (docs/PORTING.md §4.1): the pre-launch ones as the mod is
 * constructed, the main ones as registries open, then the dedicated server or the client ones. Fabric Loader runs every
 * initializer from {@code Minecraft}'s constructor on a client, the game instance set, and so does Forge's mod loading
 * there ({@code ClientModLoader.begin}), so the client ones run right after the main ones, with the registries still
 * open. The rest of Fabric API on Forge's events follows (its loot API is native: {@code forge.loot}). It also adds the
 * Fabric mods discovery didn't load to Forge's loading warnings, which Forge reads once every mod has loaded: the server
 * logs them, the client shows them on its warnings screen.
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
		FabricatedState state = FabricatedState.get();
		// Native fabric-loot-api-v3 on Forge's loot table loading (see LootBridge). Each bridge below stands down when
		// another mod provides its module (the provider policy): that mod's copy of the module is the one loaded.
		if (ours(state, "fabric-loot-api-v3")) LootCompat.register();
		if (state == null) {
			LOGGER.warn("Fabricated's discovery did not run; Fabric mods are not loaded");
			return;
		}
		// Fabric API's default attribute event, passed through, in Forge's attribute modification (see DefaultAttributeModify).
		if (ours(state, "fabric-object-builder-api-v1")) DefaultAttributeModify.register();
		// Fabric storages and Forge's item and fluid handlers on blocks, each seen by the other side's code.
		if (FabricLoader.getInstance().isModLoaded("fabric-transfer-api-v1") && ours(state, "fabric-transfer-api-v1")) TransferBridge.register();
		for (String text : ForgeLoaderHooks.notLoaded()) {
			LoadingWarnings.add(new ModLoadingWarning(context.getContainer().getModInfo(), ModLoadingStage.CONSTRUCT, AS_IS, text));
		}
		if (!FMLLoader.getDist().isDedicatedServer()) {
			// Fabric mods' config screens in Forge's mod list, once their initializers ran.
			ClientSupport.init(state);
			// Fabric API's screen drawing events, from Forge's (see ScreenDrawEvents; its input events: ScreenInputEvents).
			if (ours(state, "fabric-screen-api-v1")) ScreenDrawEvents.register();
		}
		IModInfo self = context.getContainer().getModInfo();
		EntrypointRunner runner = new EntrypointRunner(state.runtime(), new EntrypointRunner.ContextHook() {
			@Override
			public void enter(String modId) {
			}

			@Override
			public void exit() {
			}
		});
		List<EntrypointRunner.Failure> preLaunch = runner.run("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch);
		report(state, preLaunch, "pre-launch");
		RegisterEvent.getBus(context.getModBusGroup()).addListener(event -> {
			if (initialized) return;
			if (event.getRegistryKey().equals(ForgeRegistries.Keys.ATTRIBUTES)) {
				attributesRegistered = true;
			} else if (attributesRegistered) {
				initialized = true;
				initialize(state, runner, event.getForgeRegistry(), preLaunch,
						text -> LoadingWarnings.add(new ModLoadingWarning(self, ModLoadingStage.CONSTRUCT, AS_IS, text)));
			}
		});
	}

	/**
	 * @param current   the registry whose event this is, which Forge opened and locks after it (null if Forge doesn't manage it)
	 * @param preLaunch the pre-launch entrypoints' failures
	 * @param warning   adds a text to Forge's loading warnings
	 */
	private static void initialize(FabricatedState state, EntrypointRunner runner, Object current, List<EntrypointRunner.Failure> preLaunch,
			Consumer<String> warning) {
		List<ForgeRegistry<?>> opened = new ArrayList<>();
		for (var key : RegistryManager.getVanillaRegistryKeys()) {
			ForgeRegistry<?> registry = RegistryManager.ACTIVE.getRegistry(key);
			if (registry != null && registry != current) {
				registry.unfreeze();
				opened.add(registry);
			}
		}
		List<EntrypointRunner.Failure> failures = new ArrayList<>();
		boolean server = FMLLoader.getDist().isDedicatedServer();
		if (!server) state.runtime().setGameInstance(ClientSupport.gameInstance());
		try {
			record(failures, runner.run("main", ModInitializer.class, ModInitializer::onInitialize));
			MainEntrypoints.markRan();
			if (server) {
				record(failures, runner.run("server", DedicatedServerModInitializer.class, DedicatedServerModInitializer::onInitializeServer));
			} else {
				record(failures, runner.run("client", ClientModInitializer.class, ClientModInitializer::onInitializeClient));
			}
			// Each Fabric fluid family's Forge fluid type, which Forge asks every fluid for (see FabricFluidType).
			FabricFluidType.registerAll();
		} finally {
			opened.forEach(ForgeRegistry::freeze);
		}
		report(state, failures, "main");
		// Then the failures in the report and Forge's warnings, and what waited for the initializers (see EntrypointOutcome).
		List<EntrypointRunner.Failure> all = new ArrayList<>(preLaunch);
		all.addAll(failures);
		EntrypointOutcome.complete(state, all, warning);
	}

	/** Whether Fabricated's part of a Fabric API module runs: no other mod provides the module (FabricatedState). */
	private static boolean ours(FabricatedState state, String module) {
		return state == null || !state.providedElsewhere(module);
	}

	/** Adds a phase's failures, marked at once: the registries freeze before they are reported (see MainEntrypoints). */
	private static void record(List<EntrypointRunner.Failure> failures, List<EntrypointRunner.Failure> more) {
		failures.addAll(more);
		if (!more.isEmpty()) MainEntrypoints.markFailed();
	}

	private static void report(FabricatedState state, List<EntrypointRunner.Failure> failures, String phase) {
		if (!failures.isEmpty()) MainEntrypoints.markFailed();
		for (EntrypointRunner.Failure failure : failures) LOGGER.error("Fabric entrypoint failed: {}", failure.describe(), failure.error());
		LOGGER.info("Fabricated ran the Fabric {} entrypoints of {} mod(s) on Forge ({} failed)", phase, state.mods().size(), failures.size());
	}
}
