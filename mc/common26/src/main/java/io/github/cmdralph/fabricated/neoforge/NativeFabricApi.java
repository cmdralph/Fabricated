package io.github.cmdralph.fabricated.neoforge;

import io.github.cmdralph.fabricated.loader.FabricatedState;
import io.github.cmdralph.fabricated.neoforge.lifecycle.LifecycleBridge;
import io.github.cmdralph.fabricated.neoforge.networking.NetworkingBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;

/**
 * Registers the Fabric API modules Fabricated implements natively on this Minecraft version (26.x).
 * The modules are listed as "native" in this version's fabric-api-plan.json.
 */
public final class NativeFabricApi {
	private NativeFabricApi() {
	}

	public static void register(IEventBus modBus, Dist dist, FabricatedState state) {
		LifecycleBridge.register(dist);
		NetworkingBridge.register(modBus, dist, problem -> {
			state.report().line("Networking", problem);
			state.writeReport();
		});
	}
}
