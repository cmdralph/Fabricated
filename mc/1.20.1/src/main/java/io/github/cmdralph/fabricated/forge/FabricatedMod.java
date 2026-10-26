package io.github.cmdralph.fabricated.forge;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.core.runtime.EntrypointRunner;
import io.github.cmdralph.fabricated.forge.biome.BiomeCompat;
import io.github.cmdralph.fabricated.loader.FabricatedState;
import io.github.cmdralph.fabricated.loader.ForgeLoaderHooks;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.impl.loot.table.LootTablesV1Init;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.fml.ModLoadingWarning;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.registries.DataPackRegistriesHooks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryManager;

/**
 * Fabricated's in-game mod on Minecraft Forge 47 (the forge-legacy branch; the forge branch's, ported). It runs the
 * Fabric mods' entrypoints once discovery has handed Forge their files (docs/PORTING.md §7, L3): the pre-launch ones as
 * the mod is constructed, the main and dedicated server ones as registries open. The client ones (in Minecraft's
 * constructor) and Fabric API on Forge's events follow. It also adds the Fabric mods discovery didn't load to Forge's
 * loading warnings, which Forge reads once every mod has loaded: the server logs them, the client shows them on its
 * warnings screen.
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
		// First: this mod's data pack declares a biome modifier of the type it registers, which must exist even when
		// Fabricated stops below, or worlds fail to load.
		BiomeCompat.register(context.getModEventBus());
		FabricatedState state = FabricatedState.get();
		if (state == null) {
			LOGGER.warn("Fabricated's discovery did not run; Fabric mods are not loaded");
			return;
		}
		for (String text : ForgeLoaderHooks.notLoaded()) {
			ModLoader.get().addWarning(new ModLoadingWarning(context.getContainer().getModInfo(), ModLoadingStage.CONSTRUCT, AS_IS, text));
		}
		// Native fabric-loot-tables-v1's own initializer (a native module has no entrypoints): its loot table events
		// listen to fabric-loot-api-v2's before the Fabric mods register theirs.
		new LootTablesV1Init().onInitialize();
		EntrypointRunner runner = new EntrypointRunner(state.runtime(), new EntrypointRunner.ContextHook() {
			@Override
			public void enter(String modId) {
			}

			@Override
			public void exit() {
			}
		});
		report(state, runner.run("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch), "pre-launch");
		context.getModEventBus().addListener(EventPriority.HIGHEST, false, RegisterEvent.class, event -> {
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
		for (ResourceLocation key : RegistryManager.getVanillaRegistryKeys()) {
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
		} finally {
			opened.forEach(ForgeRegistry::freeze);
		}
		report(state, failures, "main");
		addDynamicRegistries();
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

	private static void report(FabricatedState state, List<EntrypointRunner.Failure> failures, String phase) {
		for (EntrypointRunner.Failure failure : failures) LOGGER.error("Fabric entrypoint failed: {}", failure.describe(), failure.error());
		LOGGER.info("Fabricated ran the Fabric {} entrypoints of {} mod(s) on Forge ({} failed)", phase, state.mods().size(), failures.size());
	}
}
