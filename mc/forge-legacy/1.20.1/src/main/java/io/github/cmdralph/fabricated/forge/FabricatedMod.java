package io.github.cmdralph.fabricated.forge;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.metadata.ModIds;
import io.github.cmdralph.fabricated.core.runtime.EntrypointRunner;
import io.github.cmdralph.fabricated.forge.biome.BiomeCompat;
import io.github.cmdralph.fabricated.forge.entity.SleepEvents;
import io.github.cmdralph.fabricated.forge.fluid.FabricFluidType;
import io.github.cmdralph.fabricated.forge.keymapping.KeyMappingBridge;
import io.github.cmdralph.fabricated.forge.resource.FabricDefaultPacks;
import io.github.cmdralph.fabricated.forge.transfer.TransferBridge;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import io.github.cmdralph.fabricated.loader.ForgeLoaderHooks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.impl.loot.table.LootTablesV1Init;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.fml.ModLoadingWarning;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.registries.DataPackRegistriesHooks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryManager;

/**
 * Fabricated's in-game mod on Minecraft Forge 47 (the forge-legacy branch; the forge branch's, ported). It runs the
 * Fabric mods' entrypoints once discovery has handed Forge their files (docs/PORTING.md §7, L3): the pre-launch ones as
 * the mod is constructed, the main ones as registries open, then the dedicated server or the client ones. Fabric
 * Loader runs every initializer from {@code Minecraft}'s constructor on a client, the game instance set, and so does
 * Forge 47's mod loading there ({@code ClientModLoader.begin}), so the client ones run right after the main ones, with
 * the registries still open. Fabric API on Forge's events follows. It also adds the Fabric mods discovery didn't load
 * to Forge's loading warnings, which Forge reads once every mod has loaded: the server logs them, the client shows them
 * on its warnings screen.
 *
 * <p>Fabric mods register their content directly from their initializers. Forge 47 opens vanilla's registries for its
 * registration events only ({@code UNFREEZE_DATA}, then {@code LOAD_REGISTRIES}), and each registry it manages only for
 * that registry's own event; the others are locked, those whose event hasn't come yet too (the item registry, the first
 * time this ran, its vanilla view locked since Forge's vanilla snapshot). The initializers run in the event after the
 * attribute registry's, as every mod has registered its attributes by then, Forge's own among them, which the attribute
 * builders Forge patches include (a Fabric initializer building an entity's attributes needs them), with every other
 * registry Forge manages for vanilla opened for as long as they run, then locked as Forge leaves a registry after its
 * event; Forge opens each again for its own event.
 */
@Mod(FabricatedMod.MOD_ID)
public final class FabricatedMod {
	public static final String MOD_ID = "fabricated";
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	/** A warning's message pattern, which Forge formats with the mod, the stage, then the warning's arguments: the first argument as is. */
	private static final String AS_IS = "{2}";
	/** Fabric API's dynamic registry API, which a Fabric mod's registries are registered through. */
	private static final String DYNAMIC_REGISTRIES = "net.fabricmc.fabric.api.event.registry.DynamicRegistries";

	private boolean attributesRegistered;
	private boolean initialized;

	public FabricatedMod() {
		FMLJavaModLoadingContext context = FMLJavaModLoadingContext.get();
		FabricatedState state = FabricatedState.get();
		// First: this mod's data pack declares a biome modifier of the type it registers, which must exist even when
		// Fabricated stops below, or worlds fail to load. Each bridge stands down when another mod provides its module
		// (the provider policy): that mod's copy of the module is the one loaded, and does its own work.
		BiomeCompat.register(context.getModEventBus(), ours(state, "fabric-biome-api-v1"));
		if (state == null) {
			LOGGER.warn("Fabricated's discovery did not run; Fabric mods are not loaded");
			return;
		}
		for (String text : ForgeLoaderHooks.notLoaded()) {
			ModLoader.get().addWarning(new ModLoadingWarning(context.getContainer().getModInfo(), ModLoadingStage.CONSTRUCT, AS_IS, text));
		}
		// What discovery tells the player from now on: a Fabric mod's jar proposed for the class path (DOWNLOAD-1).
		ForgeLoaderHooks.warnings(text -> ModLoader.get().addWarning(new ModLoadingWarning(context.getContainer().getModInfo(), ModLoadingStage.CONSTRUCT, AS_IS, text)));
		// Fabric storages and Forge's item and fluid handlers on blocks, each seen by the other side's code, for the transfer
		// module of Fabric API's jar. The module is outside the plan, which the provider check reads: a mod providing it
		// (Forgified Fabric API) has it as a Forge mod, not a Fabric mod Fabricated runs, and bridges it itself (PROVIDER-16).
		if (state.isFabricMod(ModIds.toNeoForgeId(TransferBridge.MODULE))) TransferBridge.register();
		// Fabric's sleep time event where Forge asks its own.
		if (ours(state, "fabric-entity-events-v1")) SleepEvents.register();
		// Native fabric-loot-tables-v1's own initializer (a native module has no entrypoints): its loot table events
		// listen to fabric-loot-api-v2's before the Fabric mods register theirs, Fabricated's or those of the mod providing
		// it (Forgified Fabric API 1.20.1 has fabric-loot-api-v2 and no fabric-loot-tables-v1, PROVIDER-13). Its class
		// leaves the in-game mod with the module when another mod provides it.
		if (ours(state, "fabric-loot-tables-v1")) new LootTablesV1Init().onInitialize();
		// Fabric mods' config screens in Forge's mod list, once their initializers ran.
		if (!FMLLoader.getDist().isDedicatedServer()) ClientSupport.init(state);
		// Fabric mods' key bindings, registered with Forge's key mapping event after the client entrypoints (KEYMAP-3).
		if (!FMLLoader.getDist().isDedicatedServer() && ours(state, "fabric-key-binding-api-v1")) KeyMappingBridge.register(context.getModEventBus());
		// Fabric mods' built-in packs enabled by default, once their initializers registered them (see FabricDefaultPacks).
		if (!FMLLoader.getDist().isDedicatedServer() && ours(state, FabricDefaultPacks.MODULE)) state.api().afterEntrypoints(FabricDefaultPacks::apply);
		IModInfo self = context.getContainer().getModInfo();
		// Each entrypoint runs with a context class loader that finds Fabric mods' directories, as on Fabric (SCAN-1).
		EntrypointRunner runner = new EntrypointRunner(state.runtime(), ModDirectoryClassLoader.contextHook(state.runtime()));
		List<EntrypointRunner.Failure> preLaunch = runner.run("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch);
		report(state, preLaunch, "pre-launch");
		context.getModEventBus().addListener(EventPriority.HIGHEST, false, RegisterEvent.class, event -> {
			if (initialized) return;
			if (event.getRegistryKey().equals(ForgeRegistries.Keys.ATTRIBUTES)) {
				attributesRegistered = true;
			} else if (attributesRegistered) {
				initialized = true;
				initialize(state, runner, event.getForgeRegistry(), preLaunch,
						text -> ModLoader.get().addWarning(new ModLoadingWarning(self, ModLoadingStage.CONSTRUCT, AS_IS, text)));
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
		// The root registry too, which Forge keeps frozen through its registration events (its own registries are made in
		// NewRegistryEvent): on Fabric a mod makes its registries in its main entrypoint, through Fabric API's
		// FabricRegistryBuilder, before vanilla's freeze, which Fabric API moves after the entrypoints (CORPUS-70: Porting
		// Lib's fluid types and loot modifier serializers). The freeze after the entrypoints (BuiltInRegistriesFreeze, then
		// Forge's) freezes those registries with the rest.
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
		if (ours(state, "fabric-registry-sync-v0")) {
			addDynamicRegistries();
		} else {
			// Those the initializers made through another mod's registry sync, which hands Forge only those it had before
			// them (Forgified Fabric API's, PROVIDER-21).
			ProviderRegistries.addLateDataPackRegistries("fabric-registry-sync-v0", problem -> state.report().line("Registries", problem));
		}
		// Then the failures in the report and Forge's warnings, and what waited for the initializers (see EntrypointOutcome).
		List<EntrypointRunner.Failure> all = new ArrayList<>(preLaunch);
		all.addAll(failures);
		EntrypointOutcome.complete(state, all, warning);
	}

	/**
	 * Adds the dynamic registries the Fabric mods registered (through Fabric API's {@code DynamicRegistries}) to the data
	 * pack registries Forge loads. Upstream's registry sync makes {@code WorldLoader} load its own list, vanilla's and the
	 * Fabric mods', in place of Forge's, which lost Forge's biome and structure modifiers ("Missing registry:
	 * forge:biome_modifier"); Fabricated's Fabric API plan leaves that mixin out, so Forge's list takes the Fabric mods'
	 * registries instead. Forge fills it from its own event, which comes before the Fabric initializers, and keeps it
	 * private. A registry Forge's list has already (vanilla's) isn't added twice.
	 */
	private static void addDynamicRegistries() {
		List<?> fabric;
		try {
			fabric = (List<?>) Class.forName(DYNAMIC_REGISTRIES, true, FabricatedMod.class.getClassLoader()).getMethod("getDynamicRegistries").invoke(null);
		} catch (ClassNotFoundException e) {
			return; // no Fabric API
		} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
			LOGGER.error("Couldn't read the Fabric mods' dynamic registries; they won't load", e);
			return;
		}
		try {
			Field field = DataPackRegistriesHooks.class.getDeclaredField("DATA_PACK_REGISTRIES");
			field.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<Object> forge = (List<Object>) field.get(null);
			int added = 0;
			for (Object registry : fabric) {
				if (!forge.contains(registry)) {
					forge.add(registry);
					added++;
				}
			}
			LOGGER.info("Fabricated added {} Fabric dynamic registr{} to Forge's data pack registries", added, added == 1 ? "y" : "ies");
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.error("Couldn't add the Fabric mods' dynamic registries to Forge's; they won't load", e);
		}
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
