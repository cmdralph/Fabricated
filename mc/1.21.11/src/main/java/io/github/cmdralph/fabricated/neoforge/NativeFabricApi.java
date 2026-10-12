package io.github.cmdralph.fabricated.neoforge;

import io.github.cmdralph.fabricated.loader.FabricatedState;
import io.github.cmdralph.fabricated.neoforge.entity.EntityEventsBridge;
import io.github.cmdralph.fabricated.neoforge.interaction.InteractionBridge;
import io.github.cmdralph.fabricated.neoforge.item.ItemApiBridge;
import io.github.cmdralph.fabricated.neoforge.lifecycle.LifecycleBridge;
import io.github.cmdralph.fabricated.neoforge.networking.NetworkingBridge;
import io.github.cmdralph.fabricated.neoforge.objectbuilder.ObjectBuilderBridge;
import io.github.cmdralph.fabricated.neoforge.resource.ResourceLoaderBridge;
import net.fabricmc.fabric.impl.object.builder.TradeOfferInternals;
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
		EntityEventsBridge.register();
		InteractionBridge.register(dist);
		ItemApiBridge.register(modBus, dist);
		// The 1.21.11 API has no FabricDefaultAttributeRegistry.MODIFY.
		ObjectBuilderBridge.register(modBus, modifier -> {
		});
		TradeOfferInternals.register();
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
