package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 1.21.1 part of the CI test mod: exercises Fabricated's native fabric-object-builder-api-v1 (entity type builder with
 * default attributes and a spawn placement, block entity types, villager trades, a point of interest; 0.116 has no
 * entity data serializer registry, and its block entity builder no command flag).
 * Everything is checked once the server has started and logged as one marker line.
 */
public final class ObjectBuilderTest implements ModInitializer {
	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("fabricated_testmod", path);
	}

	@Override
	public void onInitialize() {
		ResourceKey<EntityType<?>> mobKey = ResourceKey.create(Registries.ENTITY_TYPE, id("test_mob"));
		EntityType<TestMob> mob = Registry.register(BuiltInRegistries.ENTITY_TYPE, mobKey,
				FabricEntityType.Builder.createMob(TestMob::new, MobCategory.CREATURE, builder -> builder
						.defaultAttributes(() -> Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 42))
						.spawnRestriction(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (type, level, reason, pos, random) -> true))
						.alwaysUpdateVelocity(false)
						.build(mobKey.location().toString()));

		Block stone = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace("stone"));
		Block dirt = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace("dirt"));
		BlockEntityType<BarrelBlockEntity> blockEntity = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("test_block_entity"),
				FabricBlockEntityTypeBuilder.create(BarrelBlockEntity::new, stone).build());
		// Fabricated makes every block entity type Fabric's, as upstream's interface injection does on Fabric.
		((FabricBlockEntityType) blockEntity).addSupportedBlock(dirt);

		// One of the farmer's level-2 trades, offered at level 1 too; NeoForge rebuilds the tables when data loads.
		VillagerTrades.ItemListing listing = VillagerTrades.TRADES.get(VillagerProfession.FARMER).get(2)[0];
		TradeOfferHelper.registerVillagerOffers(VillagerProfession.FARMER, 1, factories -> factories.add(listing));

		// A point-of-interest type registered through vanilla's own registration, as Fabric API's helper and WorldWeaver
		// do: NeoForge maps its states too (BetterEnd, POI-1).
		Block sponge = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace("sponge"));
		PoiType poi = PointOfInterestHelper.register(id("test_poi"), 1, 1, sponge);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			List<String> failed = new ArrayList<>();
			if (PoiTypes.forState(sponge.defaultBlockState()).map(holder -> holder.value() != poi).orElse(true)) failed.add("poi");
			AttributeSupplier attributes = DefaultAttributes.hasSupplier(mob) ? DefaultAttributes.getSupplier(mob) : null;
			if (attributes == null || attributes.getBaseValue(Attributes.MAX_HEALTH) != 42) failed.add("attributes");
			if (SpawnPlacements.getPlacementType(mob) != SpawnPlacementTypes.ON_GROUND
					|| SpawnPlacements.getHeightmapType(mob) != Heightmap.Types.MOTION_BLOCKING_NO_LEAVES) failed.add("spawn_placement");
			if (mob.trackDeltas()) failed.add("velocity");
			if (!blockEntity.isValid(stone.defaultBlockState()) || !blockEntity.isValid(dirt.defaultBlockState())) failed.add("block_entity");
			if (!Arrays.asList(VillagerTrades.TRADES.get(VillagerProfession.FARMER).get(1)).contains(listing)) failed.add("trades");
			if (failed.isEmpty()) {
				TestMod.LOGGER.info("FABRICATED-TEST object builder OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST object builder FAILED: {}", failed);
			}
		});
	}
}
