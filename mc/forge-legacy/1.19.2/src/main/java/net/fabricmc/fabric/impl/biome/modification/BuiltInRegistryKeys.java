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

import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Utility class for getting the registry keys of built-in worldgen objects and throwing proper exceptions if they
 * are not registered.
 */
public final class BuiltInRegistryKeys {
	private BuiltInRegistryKeys() {
	}

	public static ResourceKey<Structure> get(Structure structureFeature) {
		return BuiltinRegistries.STRUCTURES.getResourceKey(structureFeature)
				.orElseThrow(() -> new IllegalArgumentException("Given structure is not built-in: " + structureFeature));
	}

	public static ResourceKey<ConfiguredFeature<?, ?>> get(ConfiguredFeature<?, ?> configuredFeature) {
		return BuiltinRegistries.CONFIGURED_FEATURE.getResourceKey(configuredFeature)
				.orElseThrow(() -> new IllegalArgumentException("Given configured feature is not built-in: " + configuredFeature));
	}

	public static ResourceKey<PlacedFeature> get(PlacedFeature placedFeature) {
		return BuiltinRegistries.PLACED_FEATURE.getResourceKey(placedFeature)
				.orElseThrow(() -> new IllegalArgumentException("Given placed feature is not built-in: " + placedFeature));
	}

	public static ResourceKey<ConfiguredWorldCarver<?>> get(ConfiguredWorldCarver<?> configuredCarver) {
		return BuiltinRegistries.CONFIGURED_CARVER.getResourceKey(configuredCarver)
				.orElseThrow(() -> new IllegalArgumentException("Given configured carver is not built-in: " + configuredCarver));
	}
}
