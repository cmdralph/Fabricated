package io.github.cmdralph.fabricated.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.runtime.EntrypointRunner;
import io.github.cmdralph.fabricated.forge.fluid.FabricFluidType;
import io.github.cmdralph.fabricated.forge.keymapping.KeyMappingBridge;
import io.github.cmdralph.fabricated.forge.loot.LootCompat;
import io.github.cmdralph.fabricated.forge.objectbuilder.DefaultAttributeModify;
import io.github.cmdralph.fabricated.forge.rendering.HudBridge;
import io.github.cmdralph.fabricated.forge.resource.FabricDefaultPacks;
import io.github.cmdralph.fabricated.forge.screen.ScreenDrawEvents;
import io.github.cmdralph.fabricated.forge.transfer.TransferBridge;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import io.github.cmdralph.fabricated.loader.ForgeLoaderHooks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * open. The rest of Fabric API on Forge's events follows (its loot and key mapping APIs are native: {@code forge.loot},
 * {@code forge.keymapping}). It also adds the Fabric mods discovery didn't load to Forge's loading warnings, which Forge
 * reads once every mod has loaded: the server logs them, the client shows them on its warnings screen.
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
		// What discovery tells the player from now on: a Fabric mod's jar proposed for the class path (DOWNLOAD-1).
		ForgeLoaderHooks.warnings(text -> LoadingWarnings.add(new ModLoadingWarning(context.getContainer().getModInfo(), ModLoadingStage.CONSTRUCT, AS_IS, text)));
		if (!FMLLoader.getDist().isDedicatedServer()) {
			// Fabric mods' config screens in Forge's mod list, once their initializers ran.
			ClientSupport.init(state);
			// Fabric API's screen drawing events, from Forge's (see ScreenDrawEvents; its input events: ScreenInputEvents).
			if (ours(state, "fabric-screen-api-v1")) ScreenDrawEvents.register();
			// Fabric API's HUD elements around vanilla's, from Forge's HUD layers (see HudBridge).
			if (ours(state, "fabric-rendering-v1")) HudBridge.register();
			// Native key mappings (key bindings on Forge 61), registered on Forge's key mapping event (see KeyMappingBridge).
			if (ours(state, KeyMappingBridge.MODULE)) KeyMappingBridge.register();
			// Fabric mods' built-in packs enabled by default, once their initializers registered them (see FabricDefaultPacks).
			if (ours(state, FabricDefaultPacks.MODULE)) state.api().afterEntrypoints(FabricDefaultPacks::apply);
		}
		IModInfo self = context.getContainer().getModInfo();
		// Each entrypoint runs with a context class loader that finds Fabric mods' directories, as on Fabric (SCAN-1).
		EntrypointRunner runner = new EntrypointRunner(state.runtime(), ModDirectoryClassLoader.contextHook(state.runtime()));
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
		// The root registry too, which Forge keeps frozen through its registration events (its own registries are made in
		// NewRegistryEvent): on Fabric a mod makes its registries in its main entrypoint, through Fabric API's
		// FabricRegistryBuilder, before vanilla's freeze, which Fabric API moves after the entrypoints (CORPUS-70: Friends
		// and Foes' entity data serializers, through Fabric API's own registry of them). The freeze after the entrypoints
		// (BuiltInRegistriesFreeze, then Forge's) freezes those registries with the rest.
		MappedRegistry<?> root = (MappedRegistry<?>) BuiltInRegistries.REGISTRY;
		root.unfreeze();
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
			root.freeze();
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
