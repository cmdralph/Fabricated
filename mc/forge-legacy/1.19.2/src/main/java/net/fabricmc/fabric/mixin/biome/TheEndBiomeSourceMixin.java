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
package net.fabricmc.fabric.mixin.biome;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.TheEndBiomeSource;

import net.fabricmc.fabric.impl.biome.TheEndBiomeData;

@Mixin(TheEndBiomeSource.class)
public class TheEndBiomeSourceMixin extends BiomeSourceMixin {
	@Unique
	private Supplier<TheEndBiomeData.Overrides> overrides;

	@Unique
	private boolean biomeSetModified = false;

	@Unique
	private boolean hasCheckedForModifiedSet = false;

	@Inject(method = "<init>(Lnet/minecraft/core/Registry;)V", at = @At("RETURN"))
	private void init(Registry<Biome> biomeRegistry, CallbackInfo ci) {
		overrides = Suppliers.memoize(() -> TheEndBiomeData.createOverrides(biomeRegistry));
	}

	@Inject(method = "getNoiseBiome", at = @At("RETURN"), cancellable = true)
	private void getWeightedEndBiome(int biomeX, int biomeY, int biomeZ, Climate.Sampler noise, CallbackInfoReturnable<Holder<Biome>> cir) {
		cir.setReturnValue(overrides.get().pick(biomeX, biomeY, biomeZ, noise, cir.getReturnValue()));
	}

	/**
	 * Fabricated: Forge 43 keeps a biome source's biomes behind a lazy supplier, where upstream adds the custom End biomes
	 * to vanilla's set in place: they are added to a copy of the set the supplier gives, as on Forge 47
	 * ({@link BiomeSourceMixin}).
	 */
	@Override
	protected Set<Holder<Biome>> fabric_modifyBiomeSet(Set<Holder<Biome>> biomes) {
		if (!hasCheckedForModifiedSet) {
			hasCheckedForModifiedSet = true;
			biomeSetModified = !overrides.get().customBiomes.isEmpty();
		}

		if (biomeSetModified) {
			var modifiedBiomes = new LinkedHashSet<>(biomes);
			modifiedBiomes.addAll(overrides.get().customBiomes);
			return Collections.unmodifiableSet(modifiedBiomes);
		}

		return biomes;
	}
}
