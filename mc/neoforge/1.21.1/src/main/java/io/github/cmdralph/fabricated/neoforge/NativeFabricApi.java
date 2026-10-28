package io.github.cmdralph.fabricated.neoforge;

import java.util.function.Predicate;

import io.github.cmdralph.fabricated.loader.FabricatedState;
import io.github.cmdralph.fabricated.neoforge.entity.EntityEventsBridge;
import io.github.cmdralph.fabricated.neoforge.interaction.InteractionBridge;
import io.github.cmdralph.fabricated.neoforge.item.ItemApiBridge;
import io.github.cmdralph.fabricated.neoforge.loot.LootBridge;
import io.github.cmdralph.fabricated.neoforge.lifecycle.LifecycleBridge;
import io.github.cmdralph.fabricated.neoforge.networking.NetworkingBridge;
import io.github.cmdralph.fabricated.neoforge.objectbuilder.ObjectBuilderBridge;
import io.github.cmdralph.fabricated.neoforge.rendering.FabricFluidClientExtensions;
import io.github.cmdralph.fabricated.neoforge.resource.ResourceLoaderBridge;
import io.github.cmdralph.fabricated.neoforge.transfer.TransferBridge;
import net.fabricmc.fabric.impl.content.registry.FuelRegistryImpl;
import net.fabricmc.fabric.impl.lookup.ApiLookupImpl;
import net.fabricmc.fabric.impl.object.builder.TradeOfferInternals;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;

/**
 * Registers the Fabric API modules Fabricated implements natively on this Minecraft version (1.21.1).
 * The modules are listed as "native" in this version's fabric-api-plan.json.
 */
public final class NativeFabricApi {
	private NativeFabricApi() {
	}

	public static void register(IEventBus modBus, Dist dist, FabricatedState state) {
		// A module another mod provides is that mod's: its bridge stays off here, as its packages and mixins do (core's
		// NativeModules), or its events would fire twice.
		Predicate<String> ours = module -> !state.providedElsewhere(module);
		if (ours.test("fabric-lifecycle-events-v1")) LifecycleBridge.register(dist);
		if (ours.test("fabric-entity-events-v1")) EntityEventsBridge.register();
		if (ours.test("fabric-events-interaction-v0")) InteractionBridge.register(dist);
		if (ours.test("fabric-loot-api-v3")) LootBridge.register();
		// fabric-api-lookup-api-v1's own initializer (it only registers a check at server start).
		if (ours.test("fabric-api-lookup-api-v1")) new ApiLookupImpl().onInitialize();
		if (ours.test("fabric-item-api-v1")) ItemApiBridge.register(modBus, dist);
		if (ours.test("fabric-transfer-api-v1")) TransferBridge.register(modBus);
		if (ours.test("fabric-content-registries-v0")) FuelRegistryImpl.registerBurnTimes();
		if (dist.isClient()) {
			// NeoForge draws fluids through their types' client extensions on this version (Fabric mods' fluids, whoever
			// provides the API).
			FabricFluidClientExtensions.register(modBus);
		}
		if (ours.test("fabric-object-builder-api-v1")) {
			// The 1.21.1 API has no FabricDefaultAttributeRegistry.MODIFY.
			ObjectBuilderBridge.register(modBus, state.api()::entrypointsComplete, modifier -> {
			});
			TradeOfferInternals.register();
		}
		if (ours.test("fabric-networking-api-v1")) {
			NetworkingBridge.register(modBus, dist, problem -> {
				state.report().line("Networking", problem);
				state.writeReport();
			});
		}
		if (ours.test("fabric-resource-loader-v0")) {
			ResourceLoaderBridge.register(modBus, dist, problem -> {
				state.report().line("Resource loading", problem);
				state.writeReport();
			});
		}
	}
}
