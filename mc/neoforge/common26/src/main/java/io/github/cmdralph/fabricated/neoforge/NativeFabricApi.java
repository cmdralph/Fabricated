package io.github.cmdralph.fabricated.neoforge;

import java.util.function.Predicate;

import io.github.cmdralph.fabricated.loader.FabricatedState;
import io.github.cmdralph.fabricated.neoforge.block.BlockApiCompat;
import io.github.cmdralph.fabricated.neoforge.entity.EntityEventsBridge;
import io.github.cmdralph.fabricated.neoforge.interaction.ClientHotbarScroll;
import io.github.cmdralph.fabricated.neoforge.interaction.InteractionBridge;
import io.github.cmdralph.fabricated.neoforge.item.ItemApiBridge;
import io.github.cmdralph.fabricated.neoforge.loot.LootBridge;
import io.github.cmdralph.fabricated.neoforge.lifecycle.LifecycleBridge;
import io.github.cmdralph.fabricated.neoforge.networking.NetworkingBridge;
import io.github.cmdralph.fabricated.neoforge.objectbuilder.ObjectBuilderBridge;
import io.github.cmdralph.fabricated.neoforge.rendering.FabricBlockRendering;
import io.github.cmdralph.fabricated.neoforge.rendering.HudBridge;
import io.github.cmdralph.fabricated.neoforge.rendering.PictureInPictureBridge;
import io.github.cmdralph.fabricated.neoforge.rendering.ScreenRenderBridge;
import io.github.cmdralph.fabricated.neoforge.resource.ClientResourceLoaderBridge;
import io.github.cmdralph.fabricated.neoforge.resource.ResourceLoaderBridge;
import io.github.cmdralph.fabricated.neoforge.transfer.TransferBridge;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.impl.lookup.ApiLookupImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
		// A module another mod provides is that mod's: its bridge stays off here, as its packages and mixins do (core's
		// NativeModules), or its events would fire twice.
		Predicate<String> ours = module -> !state.providedElsewhere(module);
		if (ours.test("fabric-lifecycle-events-v1")) LifecycleBridge.register(dist);
		if (ours.test("fabric-entity-events-v1")) EntityEventsBridge.register();
		if (ours.test("fabric-events-interaction-v0")) InteractionBridge.register(dist);
		if (ours.test("fabric-loot-api-v3")) LootBridge.register();
		// fabric-api-lookup-api-v1's own initializer (it only registers a check at server start).
		if (ours.test("fabric-api-lookup-api-v1")) new ApiLookupImpl().onInitialize();
		if (ours.test("fabric-transfer-api-v1")) TransferBridge.register(modBus);
		if (dist.isClient()) {
			// A renderer for Fabric's renderer API, once the initializers had their chance to register one.
			if (ours.test("fabric-renderer-api-v1")) state.api().afterEntrypoints(FabricBlockRendering::registerFallbackRenderer);
			if (ours.test("fabric-events-interaction-v0")) ClientHotbarScroll.register();
			if (ours.test("fabric-rendering-v1")) {
				HudBridge.register(modBus);
				PictureInPictureBridge.register(modBus);
			}
			if (ours.test("fabric-screen-api-v1")) ScreenRenderBridge.register();
		}
		if (ours.test("fabric-item-api-v1")) ItemApiBridge.register(modBus, dist);
		if (ours.test("fabric-block-api-v1")) BlockApiCompat.register(modBus);
		if (ours.test("fabric-object-builder-api-v1")) {
			ObjectBuilderBridge.register(modBus, state.api()::entrypointsComplete, modifier -> FabricDefaultAttributeRegistry.MODIFY.invoker().modify(new FabricDefaultAttributeRegistry.ModifyContext() {
				@Override
				public void modify(Predicate<EntityType<? extends LivingEntity>> types, FabricDefaultAttributeRegistry.ModifyConsumer consumer) {
					modifier.modify(types, consumer::accept);
				}
			}));
		}
		if (ours.test("fabric-networking-api-v1")) {
			NetworkingBridge.register(modBus, dist, problem -> {
				state.report().line("Networking", problem);
				state.writeReport();
			});
		}
		if (ours.test("fabric-resource-loader-v1")) {
			ResourceLoaderBridge.register(modBus, dist, problem -> {
				state.report().line("Resource loading", problem);
				state.writeReport();
			});
		}
		// Listeners mods register the vanilla way before NeoForge's event (vanilla behaviour, whoever provides the API).
		if (dist.isClient()) ClientResourceLoaderBridge.registerEarlyListeners(modBus);
	}
}
