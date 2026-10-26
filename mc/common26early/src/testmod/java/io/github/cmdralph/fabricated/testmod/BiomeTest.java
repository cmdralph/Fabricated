package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * CI test mod part for Fabricated's native fabric-biome-api-v1 (26.1.2 and 26.2, BIOME-1): Fabric biome modifications
 * must reach the biome NeoForge reads. A mob spawn added to plains and a changed temperature (upstream's weather
 * changes read a field NeoForge's coremod keeps private), checked once the server has started; and a sky colour, an
 * environment attribute NeoForge's biome info doesn't hold on this version, the only change to the desert, which must
 * then be sent to clients rather than read from their own copy.
 */
public final class BiomeTest implements ModInitializer {
	// Plains' own is 0.8: close enough to change nothing that generates.
	private static final float TEMPERATURE = 0.81F;
	private static final int SKY_COLOR = 0x6EB1FF;

	@Override
	public void onInitialize() {
		EntityType<?> fox = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("fox"));
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.PLAINS), MobCategory.CREATURE, fox, 5, 1, 2);
		BiomeModifications.create(Identifier.fromNamespaceAndPath("fabricated_testmod", "warmer_plains"))
				.add(ModificationPhase.REPLACEMENTS, BiomeSelectors.includeByKey(Biomes.PLAINS), context -> context.getWeather().setTemperature(TEMPERATURE));
		BiomeModifications.create(Identifier.fromNamespaceAndPath("fabricated_testmod", "desert_sky"))
				.add(ModificationPhase.REPLACEMENTS, BiomeSelectors.includeByKey(Biomes.DESERT), context -> context.getEffects().setSkyColor(SKY_COLOR));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Registry<Biome> biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
			Biome plains = biomes.getValueOrThrow(Biomes.PLAINS);
			Biome desert = biomes.getValueOrThrow(Biomes.DESERT);
			List<String> failed = new ArrayList<>();
			if (plains.getMobSettings().getMobs(MobCategory.CREATURE).unwrap().stream().noneMatch(spawn -> spawn.value().type() == fox)) {
				failed.add("spawn");
			}
			if (plains.getBaseTemperature() != TEMPERATURE) failed.add("temperature " + plains.getBaseTemperature());
			int sky = desert.getAttributes().applyModifier(EnvironmentAttributes.SKY_COLOR, 0);
			if (sky != SKY_COLOR) failed.add("sky colour " + Integer.toHexString(sky));
			if (biomes.registrationInfo(Biomes.DESERT).flatMap(RegistrationInfo::knownPackInfo).isPresent()) failed.add("desert still read from the known pack");
			if (failed.isEmpty()) {
				TestMod.LOGGER.info("FABRICATED-TEST biome modifications OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST biome modifications FAILED: {}", failed);
			}
		});
	}
}
