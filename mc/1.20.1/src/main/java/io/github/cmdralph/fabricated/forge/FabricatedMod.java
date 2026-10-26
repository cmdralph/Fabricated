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
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryManager;

/**
 * Fabricated's in-game mod on Minecraft Forge 47 (the forge-legacy branch; the forge branch's, ported). It runs the
 * Fabric mods' entrypoints once discovery has handed Forge their files (docs/PORTING.md §7, L3): the pre-launch ones as
 * the mod is constructed, the main and dedicated server ones as registries open. The client ones (in Minecraft's
 * constructor) and Fabric API on Forge's events follow.
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

	private boolean attributesRegistered;
	private boolean initialized;

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
		report(state, runner.run("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch), "pre-launch");
		FMLJavaModLoadingContext.get().getModEventBus().addListener(EventPriority.HIGHEST, false, RegisterEvent.class, event -> {
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
	}

	private static void report(FabricatedState state, List<EntrypointRunner.Failure> failures, String phase) {
		for (EntrypointRunner.Failure failure : failures) LOGGER.error("Fabric entrypoint failed: {}", failure.describe(), failure.error());
		LOGGER.info("Fabricated ran the Fabric {} entrypoints of {} mod(s) on Forge ({} failed)", phase, state.mods().size(), failures.size());
	}
}
