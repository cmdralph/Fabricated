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

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

import org.jetbrains.annotations.UnmodifiableView;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import net.minecraft.world.attribute.modifier.MobSpawnSettingsModifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeGenerationSettingsBuilder;
import net.neoforged.neoforge.common.world.BiomeSpecialEffectsBuilder;
import net.neoforged.neoforge.common.world.ClimateSettingsBuilder;
import net.neoforged.neoforge.common.world.EnvironmentAttributeMapBuilder;
import net.neoforged.neoforge.common.world.MobSpawnSettingsBuilder;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

import net.fabricmc.fabric.api.biome.v1.BiomeModificationContext;

/**
 * Fabric's biome modification context over NeoForge's builder of a biome's modified info (Fabricated; upstream's
 * replaces the biome's fields, see {@link BiomeModificationImpl}). Each part reads and writes its NeoForge builder
 * directly. Mob spawns are NeoForge's spawn builder, which NeoForge writes into the biome's
 * {@code NATURAL_MOB_SPAWNS} attribute (as an overlay) when it builds the info: an attribute value a mod sets for them
 * therefore replaces that builder's spawns and costs.
 */
public class BiomeModificationContextImpl implements BiomeModificationContext {
	private final RegistryAccess registries;
	private final WeatherContext weather;
	private final AttributesContext attributes;
	private final EffectsContext effects;
	private final GenerationSettingsContext generationSettings;
	private final MobSpawnSettingsContext spawnSettings;

	private final EnvironmentAttributeMapBuilder attributeMap;
	private final MobSpawnSettingsBuilder spawns;
	/** Whether {@link #spawns} is this context's own, which {@link #finish} writes: NeoForge writes its builder's only. */
	private final boolean ownSpawns;

	public BiomeModificationContextImpl(RegistryAccess registries, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
		this.registries = registries;
		this.attributeMap = builder.getAttributes();
		// NeoForge writes its spawn builder into the attribute only for a biome that has its spawns as an overlay (or none).
		// For any other (a data pack's biome, say) it leaves the attribute alone and the builder starts empty: the spawns
		// are then read from the attribute and set on it once the modifiers ran, as upstream sets them.
		EnvironmentAttributeMap.Entry<MobSpawnSettings, ?> entry = attributeMap.get(EnvironmentAttributes.NATURAL_MOB_SPAWNS);
		this.ownSpawns = entry != null && entry.modifier() != MobSpawnSettingsModifier.overlay();
		this.spawns = ownSpawns ? new MobSpawnSettingsBuilder(entry.applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS.defaultValue()))
				: builder.getMobSpawnSettings();
		this.weather = new WeatherContextImpl(builder.getClimateSettings());
		this.attributes = new AttributesContextImpl(attributeMap, spawns);
		this.effects = new EffectsContextImpl(builder.getSpecialEffects(), attributes);
		this.generationSettings = new GenerationSettingsContextImpl(builder.getGenerationSettings());
		this.spawnSettings = new SpawnSettingsContextImpl(spawns, attributes);
	}

	/** Called once the phase's modifiers ran: sets the spawns NeoForge wouldn't write (see the constructor). */
	void finish() {
		if (ownSpawns) attributeMap.set(EnvironmentAttributes.NATURAL_MOB_SPAWNS, spawns.build());
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
	public MobSpawnSettingsContext getMobSpawnSettings() {
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

		@Override
		public boolean hasPrecipitation() {
			return climate.hasPrecipitation();
		}

		@Override
		public float getTemperature() {
			return climate.getTemperature();
		}

		@Override
		public Biome.TemperatureModifier getTemperatureModifier() {
			return climate.getTemperatureModifier();
		}

		@Override
		public float getDownfall() {
			return climate.getDownfall();
		}
	}

	private record AttributesContextImpl(EnvironmentAttributeMapBuilder map, MobSpawnSettingsBuilder spawns) implements AttributesContext {
		@Override
		public void addAll(EnvironmentAttributeMap added) {
			map.putAll(added);
			if (added.contains(EnvironmentAttributes.NATURAL_MOB_SPAWNS)) replaceSpawns(added.get(EnvironmentAttributes.NATURAL_MOB_SPAWNS));
		}

		@Override
		public <T> void set(EnvironmentAttribute<T> key, T value) {
			map.set(key, value);
			if (key.equals(EnvironmentAttributes.NATURAL_MOB_SPAWNS)) replaceSpawns(map.get(EnvironmentAttributes.NATURAL_MOB_SPAWNS));
		}

		@Override
		public <T, M> void setModifier(EnvironmentAttribute<T> key, AttributeModifier<T, M> modifier, M value) {
			map.modify(key, modifier, value);
			if (key.equals(EnvironmentAttributes.NATURAL_MOB_SPAWNS)) replaceSpawns(map.get(EnvironmentAttributes.NATURAL_MOB_SPAWNS));
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> EnvironmentAttributeMap.@Nullable Entry<T, ?> get(EnvironmentAttribute<T> attribute) {
			if (!attribute.equals(EnvironmentAttributes.NATURAL_MOB_SPAWNS)) return map.get(attribute);
			// The spawns as NeoForge will write them.
			EnvironmentAttributeMap.Builder current = EnvironmentAttributeMap.builder();
			current.modify(EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettingsModifier.overlay(), spawns.build());
			return (EnvironmentAttributeMap.Entry<T, ?>) current.build().get(EnvironmentAttributes.NATURAL_MOB_SPAWNS);
		}

		@Override
		public <T> T applyModifier(EnvironmentAttribute<T> attribute, T value) {
			EnvironmentAttributeMap.Entry<T, ?> entry = get(attribute);
			return entry == null ? value : entry.applyModifier(value);
		}

		/** Makes NeoForge's spawn builder hold the spawns and costs the attribute's {@code entry} gives a biome. */
		private void replaceSpawns(EnvironmentAttributeMap.Entry<MobSpawnSettings, ?> entry) {
			MobSpawnSettings settings = entry.applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS.defaultValue());
			spawns.removeSpawns(spawn -> true);
			spawns.removeSpawnCost(spawns.getEntityTypes().toArray(EntityType<?>[]::new));
			for (MobCategory category : settings.definedCategories()) {
				addSpawns(spawns, category, settings.getMobsInCategory(category).unwrap());
			}
			settings.allSpawnCosts().forEach((type, cost) -> spawns.addMobSpawnCost(type, cost.charge(), cost.energyBudget()));
		}
	}

	private record EffectsContextImpl(BiomeSpecialEffectsBuilder effects, AttributesContext attributes) implements EffectsContext {
		@Override
		public void setFogColor(int color) {
			attributes.set(EnvironmentAttributes.FOG_COLOR, ARGB.vector3fFromRGB24(color));
		}

		@Override
		public void setWaterColor(int color) {
			effects.waterColor(color);
		}

		@Override
		public void setWaterFogColor(int color) {
			attributes.set(EnvironmentAttributes.WATER_FOG_COLOR, ARGB.vector3fFromRGB24(color));
		}

		@Override
		public void setSkyColor(int color) {
			attributes.set(EnvironmentAttributes.SKY_COLOR, ARGB.vector3fFromRGB24(color));
		}

		@Override
		public void setFoliageColorOverride(Optional<Integer> color) {
			effects.foliageColorOverride(Objects.requireNonNull(color));
		}

		@Override
		public void setDryFoliageColorOverride(Optional<Integer> color) {
			effects.dryFoliageColorOverride(Objects.requireNonNull(color));
		}

		@Override
		public void setGrassColorOverride(Optional<Integer> color) {
			effects.grassColorOverride(Objects.requireNonNull(color));
		}

		@Override
		public void setGrassColorModifier(BiomeSpecialEffects.GrassColorModifier colorModifier) {
			effects.grassColorModifier(Objects.requireNonNull(colorModifier));
		}

		@Override
		public void setMusicVolume(float volume) {
			attributes.set(EnvironmentAttributes.MUSIC_VOLUME, volume);
		}

		@Override
		public int getWaterColor() {
			return effects.waterColor();
		}

		@Override
		public Optional<Integer> getFoliageColorOverride() {
			return effects.getFoliageColorOverride();
		}

		@Override
		public Optional<Integer> getDryFoliageColorOverride() {
			return effects.getDryFoliageColorOverride();
		}

		@Override
		public Optional<Integer> getGrassColorOverride() {
			return effects.getGrassColorOverride();
		}

		@Override
		public BiomeSpecialEffects.GrassColorModifier getGrassColorModifier() {
			return effects.getGrassColorModifier();
		}
	}

	private class GenerationSettingsContextImpl implements GenerationSettingsContext {
		private final Registry<WorldCarver> carvers = registries.lookupOrThrow(Registries.CARVER);
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
		public void addCarver(ResourceKey<WorldCarver> entry) {
			generation.getCarvers().add(getHolder(carvers, entry));
		}

		@Override
		public boolean removeCarver(ResourceKey<WorldCarver> carverKey) {
			WorldCarver carver = getHolder(carvers, carverKey).value();
			return generation.getCarvers().removeIf(entry -> entry.value() == carver);
		}

		@Override
		public List<Holder<PlacedFeature>> getFeatures(GenerationStep.Decoration step) {
			return Collections.unmodifiableList(generation.getFeatures(step));
		}

		@Override
		public boolean hasFeature(GenerationStep.Decoration step, Holder<PlacedFeature> feature) {
			return generation.getFeatures(step).contains(feature);
		}

		@Override
		public List<Holder<WorldCarver>> getCarvers() {
			return Collections.unmodifiableList(generation.getCarvers());
		}

		@Override
		public boolean hasCarver(Holder<WorldCarver> carver) {
			return generation.getCarvers().contains(carver);
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

	/** Adds spawns to a category of NeoForge's spawn builder, defining the category if it isn't. */
	private static void addSpawns(MobSpawnSettingsBuilder spawns, MobCategory category, List<Weighted<MobSpawnSettings.SpawnerData>> added) {
		WeightedList.Builder<MobSpawnSettings.SpawnerData> list = spawns.getSpawner(category);
		if (list != null) {
			list.addAll(added);
		} else {
			spawns.addAllSpawns(category, WeightedList.of(added));
		}
	}

	private record SpawnSettingsContextImpl(MobSpawnSettingsBuilder spawns, AttributesContext attributes) implements MobSpawnSettingsContext {
		@Override
		public void setCreatureGenerationProbability(float probability) {
			attributes.set(EnvironmentAttributes.CREATURE_WORLD_GEN_SPAWN_PROBABILITY, probability);
		}

		@Override
		public @UnmodifiableView List<Weighted<MobSpawnSettings.SpawnerData>> getMobs(MobCategory category) {
			Objects.requireNonNull(category);
			WeightedList.Builder<MobSpawnSettings.SpawnerData> list = spawns.getSpawner(category);
			return list == null ? List.of() : list.getList();
		}

		@Override
		public void addSpawn(MobCategory category, MobSpawnSettings.SpawnerData data, int weight) {
			Objects.requireNonNull(category);
			Objects.requireNonNull(data);
			addSpawns(spawns, category, List.of(new Weighted<>(data, weight)));
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
		public void addMobCharge(EntityType<?> entityType, double charge, double energyBudget) {
			Objects.requireNonNull(entityType);
			spawns.addMobSpawnCost(entityType, charge, energyBudget);
		}

		@Override
		public void clearMobCharge(EntityType<?> entityType) {
			spawns.removeSpawnCost(entityType);
		}

		@Override
		public MobSpawnSettings.@Nullable MobSpawnCost getMobCharge(EntityType<?> entityType) {
			Objects.requireNonNull(entityType);
			return spawns.getCost(entityType);
		}

		@Override
		public Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> getMobCharges() {
			Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> costs = new HashMap<>();
			for (EntityType<?> type : spawns.getEntityTypes()) costs.put(type, spawns.getCost(type));
			return Collections.unmodifiableMap(costs);
		}

		@Override
		public @UnmodifiableView Set<MobCategory> getMobCategories() {
			Set<MobCategory> categories = EnumSet.noneOf(MobCategory.class);

			for (MobCategory category : spawns.getSpawnerTypes()) {
				if (!spawns.getSpawner(category).getList().isEmpty()) {
					categories.add(category);
				}
			}

			return Collections.unmodifiableSet(categories);
		}

		@Override
		public @UnmodifiableView Map<MobCategory, List<Weighted<MobSpawnSettings.SpawnerData>>> getMobs() {
			Map<MobCategory, List<Weighted<MobSpawnSettings.SpawnerData>>> mobs = new EnumMap<>(MobCategory.class);

			for (MobCategory category : getMobCategories()) {
				mobs.put(category, getMobs(category));
			}

			return Collections.unmodifiableMap(mobs);
		}
	}
}
