package io.github.cmdralph.fabricated.neoforge;

import io.github.cmdralph.fabricated.loader.FabricatedState;
import io.github.cmdralph.fabricated.neoforge.entity.EntityEventsBridge;
import io.github.cmdralph.fabricated.neoforge.interaction.InteractionBridge;
import io.github.cmdralph.fabricated.neoforge.item.ItemApiBridge;
import io.github.cmdralph.fabricated.neoforge.loot.LootBridge;
import io.github.cmdralph.fabricated.neoforge.lifecycle.LifecycleBridge;
import io.github.cmdralph.fabricated.neoforge.networking.NetworkingBridge;
import io.github.cmdralph.fabricated.neoforge.objectbuilder.ObjectBuilderBridge;
import io.github.cmdralph.fabricated.neoforge.rendering.FabricFluidClientExtensions;
import io.github.cmdralph.fabricated.neoforge.rendering.HudBridge;
import io.github.cmdralph.fabricated.neoforge.rendering.HudRenderCallbackBridge;
import io.github.cmdralph.fabricated.neoforge.rendering.ScreenRenderBridge;
import io.github.cmdralph.fabricated.neoforge.resource.ResourceLoaderBridge;
import io.github.cmdralph.fabricated.neoforge.transfer.TransferBridge;
import net.fabricmc.fabric.impl.lookup.ApiLookupImpl;
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
		LootBridge.register();
		// fabric-api-lookup-api-v1's own initializer (it only registers a check at server start).
		new ApiLookupImpl().onInitialize();
		TransferBridge.register(modBus);
		ItemApiBridge.register(modBus, dist);
		if (dist.isClient()) {
			HudBridge.register(modBus);
			HudRenderCallbackBridge.register();
			ScreenRenderBridge.register();
			// NeoForge draws fluids through their types' client extensions on this version.
			FabricFluidClientExtensions.register(modBus);
		}
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
