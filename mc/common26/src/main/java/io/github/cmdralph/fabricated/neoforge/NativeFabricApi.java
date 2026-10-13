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
		LifecycleBridge.register(dist);
		EntityEventsBridge.register();
		InteractionBridge.register(dist);
		LootBridge.register();
		// fabric-api-lookup-api-v1's own initializer (it only registers a check at server start).
		new ApiLookupImpl().onInitialize();
		TransferBridge.register(modBus);
		if (dist.isClient()) ClientHotbarScroll.register();
		ItemApiBridge.register(modBus, dist);
		BlockApiCompat.register(modBus);
		ObjectBuilderBridge.register(modBus, modifier -> FabricDefaultAttributeRegistry.MODIFY.invoker().modify(new FabricDefaultAttributeRegistry.ModifyContext() {
			@Override
			public void modify(Predicate<EntityType<? extends LivingEntity>> types, FabricDefaultAttributeRegistry.ModifyConsumer consumer) {
				modifier.modify(types, consumer::accept);
			}
		}));
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
