package io.github.cmdralph.fabricated.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.command.FabricatedCommands;
import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.core.runtime.EntrypointRunner;
import io.github.cmdralph.fabricated.forge.entity.SleepEvents;
import io.github.cmdralph.fabricated.forge.fluid.FabricFluidType;
import io.github.cmdralph.fabricated.forge.interaction.InteractionEvents;
import io.github.cmdralph.fabricated.forge.keymapping.KeyMappingBridge;
import io.github.cmdralph.fabricated.forge.loot.LootCompat;
import io.github.cmdralph.fabricated.forge.resource.FabricDefaultPacks;
import io.github.cmdralph.fabricated.forge.transfer.TransferBridge;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import io.github.cmdralph.fabricated.loader.ForgeLoaderHooks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
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
 * Fabricated's in-game mod on Minecraft Forge 52 (Minecraft 1.21.1). It runs the Fabric mods' entrypoints once
 * discovery has handed Forge their files: the pre-launch ones as the mod is constructed, the main ones as registries
 * open, then the dedicated server or the client ones, as on Forge 47 and 61 to 66. Forge 52's event bus is Forge 47's
 * (EventBus 6: listeners on {@link MinecraftForge#EVENT_BUS} and the mod's own bus), its game Forge 61's in most of
 * what Fabricated hooks (Mojang's names, no SRG). The rest of Fabric API on Forge's events follows: its loot and key
 * binding APIs are native ({@code forge.loot}, {@code forge.keymapping}), Fabric's sleep time event answers Forge's
 * ({@link SleepEvents}), and {@code /fabricated} ({@link FabricatedCommands}) is on Forge's command events. It also adds
 * the Fabric mods discovery didn't load to Forge's loading warnings.
 *
 * <p>Fabric mods register their content directly from their initializers. Forge opens vanilla's registries for its
 * registration events only, and each registry it manages only for that registry's own event (read in Forge 52's
 * {@code GameData} and {@code ForgeRegistry}, as Forge 47's and 61's). The initializers run in the event after the
 * attribute registry's, with every other registry Forge manages for vanilla opened for as long as they run, then locked
 * as Forge leaves a registry after its event.
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
		IEventBus modBus = context.getModEventBus();
		FabricatedState state = FabricatedState.get();
		// Native fabric-loot-api-v3 on Forge's loot table loading (see LootBridge). Each bridge below stands down when
		// another mod provides its module (the provider policy): that mod's copy of the module is the one loaded.
		if (ours(state, "fabric-loot-api-v3")) LootCompat.register();
		if (state == null) {
			LOGGER.warn("Fabricated's discovery did not run; Fabric mods are not loaded");
			return;
		}
		IModInfo self = context.getContainer().getModInfo();
		Consumer<String> warning = text -> LoadingWarnings.add(new ModLoadingWarning(self, ModLoadingStage.CONSTRUCT, AS_IS, text));
		for (String text : ForgeLoaderHooks.notLoaded()) warning.accept(text);
		// What discovery tells the player from now on: a Fabric mod's jar proposed for the class path (DOWNLOAD-1).
		ForgeLoaderHooks.warnings(warning);
		// Fabric storages and Forge's item and fluid handlers on blocks, each seen by the other side's code, for the transfer
		// module of Fabric API's jar. The module is outside the plan, which the provider check reads: a mod providing it
		// has it as a Forge mod, not a Fabric mod Fabricated runs, and bridges it itself (PROVIDER-16).
		if (state.isFabricMod(ModIds.toNeoForgeId(TransferBridge.MODULE))) TransferBridge.register();
		// Fabric's sleep time event where Forge asks its own (SLEEP-2).
		if (ours(state, "fabric-entity-events-v1")) SleepEvents.register();
		// Fabric API's attack block and use entity callbacks, passed through, from Forge's interaction events (INTERACT-1).
		if (ours(state, "fabric-events-interaction-v0")) InteractionEvents.register();
		// /fabricated, in the server's commands and, on a client, in its own, which only its player runs.
		MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> FabricatedCommands.register(event.getDispatcher(), state, false));
		if (!FMLLoader.getDist().isDedicatedServer()) {
			MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> FabricatedCommands.register(event.getDispatcher(), state, true));
			// Fabric mods' config screens in Forge's mod list, once their initializers ran.
			ClientSupport.init(state);
			// Fabric mods' key bindings, registered with Forge's key mapping event after the client entrypoints (KEYMAP-3).
			if (ours(state, "fabric-key-binding-api-v1")) KeyMappingBridge.register(modBus);
			// Fabric mods' built-in packs enabled by default, once their initializers registered them (CORPUS-104).
			if (ours(state, FabricDefaultPacks.MODULE)) state.api().afterEntrypoints(FabricDefaultPacks::apply);
		}
		// Each entrypoint runs with a context class loader that finds Fabric mods' directories, as on Fabric (SCAN-1).
		EntrypointRunner runner = new EntrypointRunner(state.runtime(), ModDirectoryClassLoader.contextHook(state.runtime()));
		List<EntrypointRunner.Failure> preLaunch = runner.run("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch);
		report(state, preLaunch, "pre-launch");
		modBus.addListener(EventPriority.HIGHEST, false, RegisterEvent.class, event -> {
			if (initialized) return;
			if (event.getRegistryKey().equals(ForgeRegistries.Keys.ATTRIBUTES)) {
				attributesRegistered = true;
			} else if (attributesRegistered) {
				initialized = true;
				initialize(state, runner, event.getForgeRegistry(), preLaunch, warning);
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
		for (ResourceLocation key : RegistryManager.getVanillaRegistryKeys()) {
			ForgeRegistry<?> registry = RegistryManager.ACTIVE.getRegistry(key);
			if (registry != null && registry != current) {
				registry.unfreeze();
				opened.add(registry);
			}
		}
		// The root registry too, which Forge keeps frozen through its registration events: on Fabric a mod makes its
		// registries in its main entrypoint, before vanilla's freeze, which Fabric API moves after the entrypoints
		// (CORPUS-70). The freeze after the entrypoints (BuiltInRegistriesFreeze, then Forge's) freezes them with the rest.
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
