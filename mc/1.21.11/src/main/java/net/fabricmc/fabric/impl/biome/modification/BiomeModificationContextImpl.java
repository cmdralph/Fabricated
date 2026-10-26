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

import org.jetbrains.annotations.UnmodifiableView;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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
import net.fabricmc.fabric.mixin.biome.BiomeAccessor;
import net.fabricmc.fabric.mixin.biome.BiomeSpecialEffectsBuilderAccessor;

/**
 * Fabric's biome modification context over NeoForge's builder of a biome's modified info (Fabricated, 1.21.11;
 * upstream's replaces the biome's fields, see {@link BiomeModificationImpl}). Climate, effects, generation settings and
 * mob spawns read and write NeoForge's builders directly. NeoForge's biome info doesn't hold a biome's environment
 * attributes, an ordinary field of the biome on these versions: those are replaced on the biome, as upstream replaces
 * them, and {@link #attributesChanged()} tells {@link BiomeModificationImpl} to sync the biome to clients, which
 * NeoForge's own check (its info before and after) can't see.
 */
public class BiomeModificationContextImpl implements BiomeModificationContext {
	private final RegistryAccess registries;
	private final WeatherContext weather;
	private final AttributesContextImpl attributes;
	private final EffectsContext effects;
	private final GenerationSettingsContext generationSettings;
	private final SpawnSettingsContext spawnSettings;

	public BiomeModificationContextImpl(RegistryAccess registries, Biome biome, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
		this.registries = registries;
		this.weather = new WeatherContextImpl(builder.getClimateSettings());
		this.attributes = new AttributesContextImpl(biome);
		this.effects = new EffectsContextImpl(builder.getSpecialEffects(), attributes);
		this.generationSettings = new GenerationSettingsContextImpl(builder.getGenerationSettings());
		this.spawnSettings = new SpawnSettingsContextImpl(builder.getMobSpawnSettings());
	}

	/** Whether a modifier replaced the biome's environment attributes. */
	boolean attributesChanged() {
		return attributes.changed;
	}

	@Override
	public WeatherContext getWeather() {
		return weather;
	}

	@Override
	public AttributesContext getAttributes() {
		return attributes;
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

	private static final class AttributesContextImpl implements AttributesContext {
		private final Biome biome;
		private boolean changed;

		AttributesContextImpl(Biome biome) {
			this.biome = biome;
		}

		@Override
		public void addAll(EnvironmentAttributeMap map) {
			EnvironmentAttributeMap.Builder attributes = current();
			attributes.putAll(map);
			replace(attributes);
		}

		@Override
		public <T> void set(EnvironmentAttribute<T> key, T value) {
			EnvironmentAttributeMap.Builder attributes = current();
			attributes.set(key, value);
			replace(attributes);
		}

		@Override
		public <T, M> void setModifier(EnvironmentAttribute<T> key, AttributeModifier<T, M> modifier, M value) {
			EnvironmentAttributeMap.Builder attributes = current();
			attributes.modify(key, modifier, value);
			replace(attributes);
		}

		private EnvironmentAttributeMap.Builder current() {
			return EnvironmentAttributeMap.builder().putAll(biome.getAttributes());
		}

		private void replace(EnvironmentAttributeMap.Builder attributes) {
			((BiomeAccessor) (Object) biome).fabricated$setAttributes(attributes.build());
			changed = true;
		}
	}

	private record EffectsContextImpl(BiomeSpecialEffectsBuilder effects, AttributesContext attributes) implements EffectsContext {
		@Override
		public void setFogColor(int color) {
			attributes.set(EnvironmentAttributes.FOG_COLOR, color);
		}

		@Override
		public void setWaterColor(int color) {
			effects.waterColor(color);
		}

		@Override
		public void setWaterFogColor(int color) {
			attributes.set(EnvironmentAttributes.WATER_FOG_COLOR, color);
		}

		@Override
		public void setSkyColor(int color) {
			attributes.set(EnvironmentAttributes.SKY_COLOR, color);
		}

		@Override
		public void setFoliageColor(Optional<Integer> color) {
			accessor().fabricated$setFoliageColorOverride(Objects.requireNonNull(color));
		}

		@Override
		public void setDryFoliageColor(Optional<Integer> color) {
			accessor().fabricated$setDryFoliageColorOverride(Objects.requireNonNull(color));
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
		public void setMusicVolume(float volume) {
			attributes.set(EnvironmentAttributes.MUSIC_VOLUME, volume);
		}

		/** NeoForge's builder on these versions sets an override only to a colour, not back to none. */
		private BiomeSpecialEffectsBuilderAccessor accessor() {
			return (BiomeSpecialEffectsBuilderAccessor) effects;
		}
	}

	private class GenerationSettingsContextImpl implements GenerationSettingsContext {
		private final Registry<ConfiguredWorldCarver<?>> carvers = registries.lookupOrThrow(Registries.CONFIGURED_CARVER);
		private final Registry<PlacedFeature> features = registries.lookupOrThrow(Registries.PLACED_FEATURE);
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
		public void addCarver(ResourceKey<ConfiguredWorldCarver<?>> entry) {
			generation.getCarvers().add(getHolder(carvers, entry));
		}

		@Override
		public boolean removeCarver(ResourceKey<ConfiguredWorldCarver<?>> carverKey) {
			ConfiguredWorldCarver<?> carver = getHolder(carvers, carverKey).value();
			return generation.getCarvers().removeIf(entry -> entry.value() == carver);
		}
	}

	/**
	 * Gets an entry from the given registry, assuming it's a registry loaded from data packs.
	 * Gives more helpful error messages if an entry is missing by checking if the modder
	 * forgot to data-gen the JSONs corresponding to their built-in objects.
	 */
	private static <T> Holder.Reference<T> getHolder(Registry<T> registry, ResourceKey<T> key) {
		Holder.Reference<T> holder = registry.get(key).orElse(null);

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
		public @UnmodifiableView List<Weighted<MobSpawnSettings.SpawnerData>> getSpawnEntries(MobCategory category) {
			Objects.requireNonNull(category);
			WeightedList.Builder<MobSpawnSettings.SpawnerData> list = spawns.getSpawner(category);
			return list == null ? List.of() : list.getList();
		}

		@Override
		public void addSpawn(MobCategory category, MobSpawnSettings.SpawnerData data, int weight) {
			Objects.requireNonNull(category);
			Objects.requireNonNull(data);
			spawns.addSpawn(category, weight, data);
		}

		@Override
		public boolean removeSpawns(BiPredicate<MobCategory, MobSpawnSettings.SpawnerData> predicate) {
			boolean anyRemoved = false;

			for (MobCategory category : spawns.getSpawnerTypes()) {
				WeightedList.Builder<MobSpawnSettings.SpawnerData> list = spawns.getSpawner(category);
				int before = list.getList().size();
				list.removeIf(entry -> predicate.test(category, entry.value()));
				anyRemoved |= list.getList().size() != before;
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
