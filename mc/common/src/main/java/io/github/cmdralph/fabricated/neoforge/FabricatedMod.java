package io.github.cmdralph.fabricated.neoforge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cmdralph.fabricated.loader.FabricatedState;
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
		FabricatedState state = FabricatedState.get();
		if (state == null) {
			LOGGER.warn("Fabricated's discovery did not run; Fabric mods are not loaded (see the warnings above in the log)");
			return;
		}
		EntrypointBridge entrypoints = new EntrypointBridge(state, dist);
		// Vanilla registries are frozen during mod construction and opened for the RegisterEvent loop. Fabric
		// mods register content directly from their initializers, so they run on the first RegisterEvent.
		modBus.addListener(EventPriority.HIGHEST, RegisterEvent.class, event -> entrypoints.runOnce());
		modBus.addListener(FMLLoadCompleteEvent.class, event -> entrypoints.complete());
		NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> FabricatedCommands.register(event.getDispatcher(), state, false));
		NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, event -> {
			if (dist.isDedicatedServer()) state.runtime().setGameInstance(event.getServer());
		});
		if (dist.isClient()) ClientSupport.init(state);
	}
}
