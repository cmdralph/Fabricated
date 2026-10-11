package io.github.cmdralph.fabricated.neoforge;

import io.github.cmdralph.fabricated.loader.FabricatedState;
import io.github.cmdralph.fabricated.neoforge.lifecycle.LifecycleBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;

/**
 * Registers the Fabric API modules Fabricated implements natively on this Minecraft version (1.21.11).
 * The modules are listed as "native" in this version's fabric-api-plan.json.
 */
public final class NativeFabricApi {
	private NativeFabricApi() {
	}

	public static void register(IEventBus modBus, Dist dist, FabricatedState state) {
		LifecycleBridge.register(dist);
	}
}
