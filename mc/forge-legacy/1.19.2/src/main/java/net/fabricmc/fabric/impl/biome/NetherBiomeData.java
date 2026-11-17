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
package net.fabricmc.fabric.impl.biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

import net.fabricmc.fabric.mixin.biome.BiomeSourceAccessor;
import net.fabricmc.fabric.mixin.biome.MultiNoiseBiomeSourceAccessor;
import net.fabricmc.fabric.mixin.biome.MultiNoiseBiomeSourcePresetAccessor;

/**
 * Internal data for modding Vanilla's {@link MultiNoiseBiomeSource.Preset#NETHER}.
 */
public final class NetherBiomeData {
	// Cached sets of the biomes that would generate from Vanilla's default biome source without consideration
	// for data packs (as those would be distinct biome sources).
	private static final Set<ResourceKey<Biome>> NETHER_BIOMES = new HashSet<>();

	private static final Map<ResourceKey<Biome>, Climate.ParameterPoint> NETHER_BIOME_NOISE_POINTS = new HashMap<>();

	private static final Logger LOGGER = LogUtils.getLogger();

	private NetherBiomeData() {
	}

	public static void addNetherBiome(ResourceKey<Biome> biome, Climate.ParameterPoint spawnNoisePoint) {
		Preconditions.checkArgument(biome != null, "Biome is null");
		Preconditions.checkArgument(spawnNoisePoint != null, "MultiNoiseUtil.NoiseValuePoint is null");
		NETHER_BIOME_NOISE_POINTS.put(biome, spawnNoisePoint);
		clearBiomeSourceCache();
	}

	public static Map<ResourceKey<Biome>, Climate.ParameterPoint> getNetherBiomeNoisePoints() {
		return NETHER_BIOME_NOISE_POINTS;
	}

	public static boolean canGenerateInNether(ResourceKey<Biome> biome) {
		if (NETHER_BIOMES.isEmpty()) {
			MultiNoiseBiomeSource source = MultiNoiseBiomeSource.Preset.NETHER.biomeSource(BuiltinRegistries.BIOME);

			for (Holder<Biome> entry : source.possibleBiomes()) {
				BuiltinRegistries.BIOME.getResourceKey(entry.value()).ifPresent(NETHER_BIOMES::add);
			}
		}

		return NETHER_BIOMES.contains(biome) || NETHER_BIOME_NOISE_POINTS.containsKey(biome);
	}

	private static void clearBiomeSourceCache() {
		NETHER_BIOMES.clear(); // Clear cached biome source data
	}

	private static Climate.ParameterList<Holder<Biome>> withModdedBiomeEntries(Climate.ParameterList<Holder<Biome>> entries, Registry<Biome> biomeRegistry) {
		if (NETHER_BIOME_NOISE_POINTS.isEmpty()) {
			return entries;
		}

		ArrayList<Pair<Climate.ParameterPoint, Holder<Biome>>> entryList = new ArrayList<>(entries.values());

		for (Map.Entry<ResourceKey<Biome>, Climate.ParameterPoint> entry : NETHER_BIOME_NOISE_POINTS.entrySet()) {
			if (biomeRegistry.containsKey(entry.getKey())) {
				entryList.add(Pair.of(entry.getValue(), biomeRegistry.getOrCreateHolderOrThrow(entry.getKey())));
			} else {
				LOGGER.warn("Nether biome {} not loaded", entry.getKey().location());
			}
		}

		return new Climate.ParameterList<>(entryList);
	}

	/**
	 * Fabricated: upstream replaces the source's biome entries and its set of possible biomes, which its access widener
	 * opens. Forge 43 keeps a biome source's possible biomes behind a lazy supplier of its own in place of vanilla's set:
	 * the supplier is replaced with one of the new set. The entries, the preset's entries and the supplier are reached
	 * through accessors.
	 */
	public static void modifyBiomeSource(Registry<Biome> biomeRegistry, BiomeSource biomeSource) {
		if (biomeSource instanceof MultiNoiseBiomeSource multiNoiseBiomeSource) {
			if (((BiomeSourceAccess) multiNoiseBiomeSource).fabric_shouldModifyBiomeEntries() && multiNoiseBiomeSource.stable(MultiNoiseBiomeSource.Preset.NETHER)) {
				Climate.ParameterList<Holder<Biome>> entries = NetherBiomeData.withModdedBiomeEntries(
						((MultiNoiseBiomeSourcePresetAccessor) MultiNoiseBiomeSource.Preset.NETHER).fabricated$getParameterSource().apply(biomeRegistry),
						biomeRegistry);
				((MultiNoiseBiomeSourceAccessor) multiNoiseBiomeSource).fabricated$setParameters(entries);
				Set<Holder<Biome>> biomes = entries.values().stream().map(Pair::getSecond).collect(Collectors.toSet());
				((BiomeSourceAccessor) multiNoiseBiomeSource).fabricated$setLazyPossibleBiomes(Suppliers.ofInstance(biomes));
				((BiomeSourceAccess) multiNoiseBiomeSource).fabric_setModifyBiomeEntries(false);
			}
		}
	}
}
