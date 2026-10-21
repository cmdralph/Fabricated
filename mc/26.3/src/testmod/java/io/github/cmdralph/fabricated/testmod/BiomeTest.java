package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;

/**
 * CI test mod part for Fabricated's native fabric-biome-api-v1 (26.3, BIOME-1): Fabric biome modifications must reach
 * the biome NeoForge reads. A mob spawn added to plains (the 26.3 corpus crashed creating a world once a mod added
 * one) and a changed temperature, both checked once the server has started.
 */
public final class BiomeTest implements ModInitializer {
	// Plains' own is 0.8: close enough to change nothing that generates.
	private static final float TEMPERATURE = 0.81F;

	@Override
	public void onInitialize() {
		EntityType<?> fox = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("fox"));
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.PLAINS), MobCategory.CREATURE, fox, 5, 1, 2);
		BiomeModifications.create(Identifier.fromNamespaceAndPath("fabricated_testmod", "warmer_plains"))
				.add(ModificationPhase.REPLACEMENTS, BiomeSelectors.includeByKey(Biomes.PLAINS), context -> context.getWeather().setTemperature(TEMPERATURE));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Biome plains = server.registryAccess().lookupOrThrow(Registries.BIOME).getValueOrThrow(Biomes.PLAINS);
			List<String> failed = new ArrayList<>();
			WeightedList<MobSpawnSettings.SpawnerData> creatures = plains.getAttributes()
					.applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS, EnvironmentAttributes.NATURAL_MOB_SPAWNS.defaultValue())
					.getMobsInCategory(MobCategory.CREATURE);
			if (creatures == null || creatures.unwrap().stream().noneMatch(spawn -> spawn.value().type() == fox)) failed.add("spawn");
			if (plains.getBaseTemperature() != TEMPERATURE) failed.add("temperature " + plains.getBaseTemperature());
			if (failed.isEmpty()) {
				TestMod.LOGGER.info("FABRICATED-TEST biome modifications OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST biome modifications FAILED: {}", failed);
			}
		});
	}
}
