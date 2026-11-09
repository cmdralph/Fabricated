package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 1.20.1 part of the CI test mod: exercises fabric-object-builder-api-v1 as Fabric API 0.92 has it (the entity type
 * builder with default attributes and a spawn placement, a block entity type, block settings from create() and of(),
 * villager trades, a point of interest, an entity data serializer registered through vanilla's method). Everything is
 * checked once the server has started and logged as one marker line.
 */
public final class ObjectBuilderTest implements ModInitializer {
	private static ResourceLocation id(String path) {
		return TestCompat.id("fabricated_testmod", path);
	}

	@Override
	public void onInitialize() {
		EntityType<TestMob> mob = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("test_mob"), FabricEntityTypeBuilder.<TestMob>createMob()
				.spawnGroup(MobCategory.CREATURE)
				.entityFactory(TestMob::new)
				.defaultAttributes(() -> Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 42))
				.spawnRestriction(SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (type, level, reason, pos, random) -> true)
				.forceTrackedVelocityUpdates(false)
				.build());

		BlockEntityType<BarrelBlockEntity> blockEntity = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("test_block_entity"),
				FabricBlockEntityTypeBuilder.create(BarrelBlockEntity::new, Blocks.STONE, Blocks.DIRT).build());
		// Mods call Fabric's factories and setters by Fabric's own names (CORPUS-39).
		boolean blockSettings = FabricBlockSettings.of() != null
				&& FabricBlockSettings.create().requiresTool().sounds(SoundType.STONE).hardness(2.0F).resistance(6.0F).nonOpaque().luminance(state -> 7) != null;
		// Fabric mods register serializers through vanilla's method (CORPUS-41).
		EntityDataSerializer<Integer> serializer = EntityDataSerializer.simple((buf, value) -> buf.writeVarInt(value), buf -> buf.readVarInt());
		EntityDataSerializers.registerSerializer(serializer);

		// One of the farmer's level-2 trades, offered at level 1 too.
		VillagerTrades.ItemListing listing = VillagerTrades.TRADES.get(VillagerProfession.FARMER).get(2)[0];
		TradeOfferHelper.registerVillagerOffers(VillagerProfession.FARMER, 1, factories -> factories.add(listing));

		// A point-of-interest type registered through vanilla's own registration, as Fabric API's helper and WorldWeaver
		// do: the platform maps its states too (BetterEnd, POI-1).
		Block sponge = Blocks.SPONGE;
		PoiType poi = PointOfInterestHelper.register(id("test_poi"), 1, 1, sponge);

		// A particle type for the particles module's client check.
		ParticleTest.register();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			List<String> failed = new ArrayList<>();
			if (PoiTypes.forState(sponge.defaultBlockState()).map(holder -> holder.value() != poi).orElse(true)) failed.add("poi");
			AttributeSupplier attributes = DefaultAttributes.hasSupplier(mob) ? DefaultAttributes.getSupplier(mob) : null;
			if (attributes == null || attributes.getBaseValue(Attributes.MAX_HEALTH) != 42) failed.add("attributes");
			if (SpawnPlacements.getPlacementType(mob) != SpawnPlacements.Type.ON_GROUND
					|| SpawnPlacements.getHeightmapType(mob) != Heightmap.Types.MOTION_BLOCKING_NO_LEAVES) failed.add("spawn_placement");
			if (mob.trackDeltas()) failed.add("velocity");
			if (!blockEntity.isValid(Blocks.STONE.defaultBlockState()) || !blockEntity.isValid(Blocks.DIRT.defaultBlockState())) failed.add("block_entity");
			if (!blockSettings) failed.add("block_settings");
			int serializerId = EntityDataSerializers.getSerializedId(serializer);
			if (serializerId < 0 || EntityDataSerializers.getSerializer(serializerId) != serializer) failed.add("data_serializer");
			if (!Arrays.asList(VillagerTrades.TRADES.get(VillagerProfession.FARMER).get(1)).contains(listing)) failed.add("trades");
			if (failed.isEmpty()) {
				TestMod.LOGGER.info("FABRICATED-TEST object builder OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST object builder FAILED: {}", failed);
			}
		});
	}
}
