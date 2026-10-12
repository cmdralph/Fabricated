package io.github.cmdralph.fabricated.neoforge;

import io.github.cmdralph.fabricated.loader.FabricatedState;
import io.github.cmdralph.fabricated.neoforge.entity.EntityEventsBridge;
import io.github.cmdralph.fabricated.neoforge.lifecycle.LifecycleBridge;
import io.github.cmdralph.fabricated.neoforge.networking.NetworkingBridge;
import io.github.cmdralph.fabricated.neoforge.objectbuilder.ObjectBuilderBridge;
import io.github.cmdralph.fabricated.neoforge.resource.ResourceLoaderBridge;
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
		EntityEventsBridge.register();
		ObjectBuilderBridge.register(modBus);
		NetworkingBridge.register(modBus, dist, problem -> {
			state.report().line("Networking", problem);
			state.writeReport();
		});
		ResourceLoaderBridge.register(modBus, dist, problem -> {
			state.report().line("Resource loading", problem);
			state.writeReport();
		});
	}
}
