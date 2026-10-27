/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.impl.biome.modification;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.AmbientAdditionsSettings;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.AmbientParticleSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeGenerationSettingsBuilder;
import net.neoforged.neoforge.common.world.BiomeSpecialEffectsBuilder;
import net.neoforged.neoforge.common.world.ClimateSettingsBuilder;
import net.neoforged.neoforge.common.world.MobSpawnSettingsBuilder;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

import net.fabricmc.fabric.api.biome.v1.BiomeModificationContext;
import net.fabricmc.fabric.mixin.biome.BiomeSpecialEffectsBuilderAccessor;

/**
 * Fabric's biome modification context over NeoForge's builder of a biome's modified info (Fabricated, 1.21.1; upstream's
 * replaces the biome's fields, see {@link BiomeModificationImpl}). Climate, effects, generation settings and mob spawns
 * read and write NeoForge's builders directly, so NeoForge sends a changed biome to clients itself.
 */
public class BiomeModificationContextImpl implements BiomeModificationContext {
	private final RegistryAccess registries;
	private final WeatherContext weather;
	private final EffectsContext effects;
	private final GenerationSettingsContext generationSettings;
	private final SpawnSettingsContext spawnSettings;

	public BiomeModificationContextImpl(RegistryAccess registries, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
		this.registries = registries;
		this.weather = new WeatherContextImpl(builder.getClimateSettings());
		this.effects = new EffectsContextImpl(builder.getSpecialEffects());
		this.generationSettings = new GenerationSettingsContextImpl(builder.getGenerationSettings());
		this.spawnSettings = new SpawnSettingsContextImpl(builder.getMobSpawnSettings());
	}

	@Override
	public WeatherContext getWeather() {
		return weather;
	}

	@Override
	public EffectsContext getEffects() {
		return effects;
	}

	@Override
	public GenerationSettingsContext getGenerationSettings() {
		return generationSettings;
	}

	@Override
	public SpawnSettingsContext getSpawnSettings() {
		return spawnSettings;
	}

	private record WeatherContextImpl(ClimateSettingsBuilder climate) implements WeatherContext {
		@Override
		public void setPrecipitation(boolean hasPrecipitation) {
			climate.setHasPrecipitation(hasPrecipitation);
		}

		@Override
		public void setTemperature(float temperature) {
			climate.setTemperature(temperature);
		}

		@Override
		public void setTemperatureModifier(Biome.TemperatureModifier temperatureModifier) {
			climate.setTemperatureModifier(Objects.requireNonNull(temperatureModifier));
		}

		@Override
		public void setDownfall(float downfall) {
			climate.setDownfall(downfall);
		}
	}

	private record EffectsContextImpl(BiomeSpecialEffectsBuilder effects) implements EffectsContext {
		@Override
		public void setFogColor(int color) {
			effects.fogColor(color);
		}

		@Override
		public void setWaterColor(int color) {
			effects.waterColor(color);
		}

		@Override
		public void setWaterFogColor(int color) {
			effects.waterFogColor(color);
		}

		@Override
		public void setSkyColor(int color) {
			effects.skyColor(color);
		}

		@Override
		public void setFoliageColor(Optional<Integer> color) {
			accessor().fabricated$setFoliageColorOverride(Objects.requireNonNull(color));
		}

		@Override
		public void setGrassColor(Optional<Integer> color) {
			accessor().fabricated$setGrassColorOverride(Objects.requireNonNull(color));
		}

		@Override
		public void setGrassColorModifier(BiomeSpecialEffects.GrassColorModifier colorModifier) {
			effects.grassColorModifier(Objects.requireNonNull(colorModifier));
		}

		@Override
		public void setParticleConfig(Optional<AmbientParticleSettings> particleConfig) {
			accessor().fabricated$setAmbientParticle(Objects.requireNonNull(particleConfig));
		}

		@Override
		public void setAmbientSound(Optional<Holder<SoundEvent>> sound) {
			accessor().fabricated$setAmbientLoopSoundEvent(Objects.requireNonNull(sound));
		}

		@Override
		public void setMoodSound(Optional<AmbientMoodSettings> sound) {
			accessor().fabricated$setAmbientMoodSettings(Objects.requireNonNull(sound));
		}

		@Override
		public void setAdditionsSound(Optional<AmbientAdditionsSettings> sound) {
			accessor().fabricated$setAmbientAdditionsSettings(Objects.requireNonNull(sound));
		}

		@Override
		public void setMusic(Optional<Music> sound) {
			accessor().fabricated$setBackgroundMusic(Objects.requireNonNull(sound));
		}

		/** NeoForge's builder sets these only to a value, not back to none. */
		private BiomeSpecialEffectsBuilderAccessor accessor() {
			return (BiomeSpecialEffectsBuilderAccessor) effects;
		}
	}

	private class GenerationSettingsContextImpl implements GenerationSettingsContext {
		private final Registry<ConfiguredWorldCarver<?>> carvers = registries.registryOrThrow(Registries.CONFIGURED_CARVER);
		private final Registry<PlacedFeature> features = registries.registryOrThrow(Registries.PLACED_FEATURE);
		private final BiomeGenerationSettingsBuilder generation;

		GenerationSettingsContextImpl(BiomeGenerationSettingsBuilder generation) {
			this.generation = generation;
		}

		@Override
		public boolean removeFeature(GenerationStep.Decoration step, ResourceKey<PlacedFeature> placedFeatureKey) {
			PlacedFeature placedFeature = getHolder(features, placedFeatureKey).value();
			return generation.getFeatures(step).removeIf(feature -> feature.value() == placedFeature);
		}

		@Override
		public void addFeature(GenerationStep.Decoration step, ResourceKey<PlacedFeature> entry) {
			Holder.Reference<PlacedFeature> feature = getHolder(features, entry);
			List<Holder<PlacedFeature>> inStep = generation.getFeatures(step);

			// Don't add the feature if it's already present
			if (!inStep.contains(feature)) inStep.add(feature);
		}

		@Override
		public void addCarver(GenerationStep.Carving step, ResourceKey<ConfiguredWorldCarver<?>> entry) {
			generation.getCarvers(step).add(getHolder(carvers, entry));
		}

		@Override
		public boolean removeCarver(GenerationStep.Carving step, ResourceKey<ConfiguredWorldCarver<?>> carverKey) {
			ConfiguredWorldCarver<?> carver = getHolder(carvers, carverKey).value();
			return generation.getCarvers(step).removeIf(entry -> entry.value() == carver);
		}
	}

	/**
	 * Gets an entry from the given registry, assuming it's a registry loaded from data packs.
	 * Gives more helpful error messages if an entry is missing by checking if the modder
	 * forgot to data-gen the JSONs corresponding to their built-in objects.
	 */
	private static <T> Holder.Reference<T> getHolder(Registry<T> registry, ResourceKey<T> key) {
		Holder.Reference<T> holder = registry.getHolder(key).orElse(null);

		if (holder == null) {
			// The key doesn't exist in the data packs
			throw new IllegalArgumentException("Couldn't find holder for " + key);
		}

		return holder;
	}

	private record SpawnSettingsContextImpl(MobSpawnSettingsBuilder spawns) implements SpawnSettingsContext {
		@Override
		public void setCreatureSpawnProbability(float probability) {
			spawns.creatureGenerationProbability(probability);
		}

		@Override
		public void addSpawn(MobCategory category, MobSpawnSettings.SpawnerData data) {
			Objects.requireNonNull(category);
			Objects.requireNonNull(data);
			spawns.addSpawn(category, data);
		}

		@Override
		public boolean removeSpawns(BiPredicate<MobCategory, MobSpawnSettings.SpawnerData> predicate) {
			boolean anyRemoved = false;

			for (MobCategory category : spawns.getSpawnerTypes()) {
				anyRemoved |= spawns.getSpawner(category).removeIf(entry -> predicate.test(category, entry));
			}

			return anyRemoved;
		}

		@Override
		public void setSpawnCost(EntityType<?> entityType, double mass, double gravityLimit) {
			Objects.requireNonNull(entityType);
			spawns.addMobCharge(entityType, mass, gravityLimit);
		}

		@Override
		public void clearSpawnCost(EntityType<?> entityType) {
			spawns.removeSpawnCost(entityType);
		}
	}
}
