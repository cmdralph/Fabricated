package io.github.cmdralph.fabricated.neoforge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.loader.FabricatedState;
import io.github.cmdralph.fabricated.neoforge.biome.BiomeCompat;
import io.github.cmdralph.fabricated.neoforge.fluid.FabricFluidType;
import io.github.cmdralph.fabricated.neoforge.registry.RegistrySyncBridge;
import net.minecraft.core.registries.Registries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Fabricated's in-game mod. It runs Fabric entrypoints at the right moment, hosts the native Fabric API
 * modules and their NeoForge event bridges, and provides the {@code /fabricated} command.
 */
@Mod(FabricatedMod.MOD_ID)
public final class FabricatedMod {
	public static final String MOD_ID = "fabricated";
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	public FabricatedMod(IEventBus modBus, Dist dist) {
		// First: this mod's data pack declares a biome modifier of the type it registers (26.3), which must exist even
		// when Fabricated stops below, or worlds fail to load.
		BiomeCompat.register(modBus);
		FabricatedState state = FabricatedState.get();
		if (state == null) {
			LOGGER.warn("Fabricated's discovery did not run; Fabric mods are not loaded (see the warnings above in the log)");
			return;
		}
		EntrypointBridge entrypoints = new EntrypointBridge(state, dist);
		// Native fabric-registry-sync-v0, first: it tracks the RegisterEvent window the initializers below run in.
		RegistrySyncBridge.register(modBus, dist, problem -> {
			state.report().line("Registries", problem);
			state.writeReport();
		});
		// Vanilla registries are frozen during mod construction and opened for the RegisterEvent loop. Fabric
		// mods register content directly from their initializers, so on a dedicated server they run inside that
		// loop (on a client, from Minecraft's constructor, with the registries opened again: see EntrypointBridge):
		// on the first RegisterEvent after the attribute registry's, which NeoForge posts first. NeoForge registers
		// its own attributes there, and every living entity's attribute builder includes them, so a Fabric
		// initializer that builds attributes (a very common thing to do) needs them registered. All registries stay
		// open until the loop ends, so Fabric mods can still register attributes.
		modBus.addListener(EventPriority.HIGHEST, RegisterEvent.class, event -> {
			if (!event.getRegistryKey().equals(Registries.ATTRIBUTE)) entrypoints.runOnce();
		});
		modBus.addListener(FMLLoadCompleteEvent.class, event -> entrypoints.complete());
		NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> FabricatedCommands.register(event.getDispatcher(), state, false));
		NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, event -> {
			if (dist.isDedicatedServer()) state.runtime().setGameInstance(event.getServer());
		});
		// NeoForge asks every fluid for its fluid type, which Fabric mods' fluids don't have.
		FabricFluidType.register(modBus);
		// Native Fabric API modules (version-specific: mc/neoforge/common26 or mc/neoforge/<version>).
		NativeFabricApi.register(modBus, dist, state);
		if (dist.isClient()) ClientSupport.init(state);
	}
}
