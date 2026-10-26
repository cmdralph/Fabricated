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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.world.ModifiableBiomeInfo;

import net.fabricmc.fabric.api.biome.v1.BiomeModificationContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;

/**
 * Upstream's list of biome modifiers, applied by Forge's biome modifiers instead of in {@code MinecraftServer}'s
 * constructor (Fabricated, L4 on Forge 47). Forge reads a biome's climate, effects, generation settings and mob spawns
 * from its modifiable biome info, captured when the biome was built, and its coremod needs the climate and effects
 * fields private to point their reads there: upstream's way of replacing those fields, through its access widener,
 * would change nothing Forge reads, and left the coremod unable to apply at all. Fabricated's biome modifier
 * ({@code FabricBiomeModifier}) calls {@link #modify} for each biome and Forge phase, with Forge's builder of the biome's
 * modified info.
 */
public class BiomeModificationImpl {
	private static final Comparator<ModifierRecord> MODIFIER_ORDER_COMPARATOR = Comparator.<ModifierRecord>comparingInt(r -> r.phase.ordinal()).thenComparingInt(r -> r.order).thenComparing(r -> r.id);

	public static final BiomeModificationImpl INSTANCE = new BiomeModificationImpl();

	private final List<ModifierRecord> modifiers = new ArrayList<>();

	private boolean modifiersUnsorted = true;

	private BiomeModificationImpl() {
	}

	public void addModifier(ResourceLocation id, ModificationPhase phase, Predicate<BiomeSelectionContext> selector, BiConsumer<BiomeSelectionContext, BiomeModificationContext> modifier) {
		Objects.requireNonNull(selector);
		Objects.requireNonNull(modifier);

		modifiers.add(new ModifierRecord(phase, id, selector, modifier));
		modifiersUnsorted = true;
	}

	public void addModifier(ResourceLocation id, ModificationPhase phase, Predicate<BiomeSelectionContext> selector, Consumer<BiomeModificationContext> modifier) {
		Objects.requireNonNull(selector);
		Objects.requireNonNull(modifier);

		modifiers.add(new ModifierRecord(phase, id, selector, modifier));
		modifiersUnsorted = true;
	}

	/**
	 * This is currently not publicly exposed but likely useful for modpack support mods.
	 */
	void changeOrder(ResourceLocation id, int order) {
		modifiersUnsorted = true;

		for (ModifierRecord modifierRecord : modifiers) {
			if (id.equals(modifierRecord.id)) {
				modifierRecord.setOrder(order);
			}
		}
	}

	private List<ModifierRecord> getSortedModifiers() {
		if (modifiersUnsorted) {
			// Resort modifiers
			modifiers.sort(MODIFIER_ORDER_COMPARATOR);
			modifiersUnsorted = false;
		}

		return modifiers;
	}

	/**
	 * Applies the modifiers of {@code phase} whose selector picks {@code biome}, in upstream's order, to Forge's builder
	 * of the biome's modified info. Selectors see the biome's climate, effects, generation settings and spawns as it was
	 * built, as upstream's do: Forge's info becomes the modified one only once every phase ran.
	 */
	public void modify(RegistryAccess registries, Holder<Biome> biome, ModificationPhase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
		ResourceKey<Biome> key = biome.unwrapKey().orElseThrow();
		BiomeSelectionContext context = null;
		BiomeModificationContextImpl modificationContext = null;

		for (ModifierRecord modifier : getSortedModifiers()) {
			if (modifier.phase != phase) continue;
			if (context == null) context = new BiomeSelectionContextImpl(registries, key, biome.value());
			if (!modifier.selector.test(context)) continue;
			if (modificationContext == null) modificationContext = new BiomeModificationContextImpl(registries, builder);
			modifier.apply(context, modificationContext);
		}
	}

	private static class ModifierRecord {
		private final ModificationPhase phase;

		private final ResourceLocation id;

		private final Predicate<BiomeSelectionContext> selector;

		private final BiConsumer<BiomeSelectionContext, BiomeModificationContext> contextSensitiveModifier;

		private final Consumer<BiomeModificationContext> modifier;

		// Whenever this is modified, the modifiers need to be resorted
		private int order;

		ModifierRecord(ModificationPhase phase, ResourceLocation id, Predicate<BiomeSelectionContext> selector, Consumer<BiomeModificationContext> modifier) {
			this.phase = phase;
			this.id = id;
			this.selector = selector;
			this.modifier = modifier;
			this.contextSensitiveModifier = null;
		}

		ModifierRecord(ModificationPhase phase, ResourceLocation id, Predicate<BiomeSelectionContext> selector, BiConsumer<BiomeSelectionContext, BiomeModificationContext> modifier) {
			this.phase = phase;
			this.id = id;
			this.selector = selector;
			this.contextSensitiveModifier = modifier;
			this.modifier = null;
		}

		@Override
		public String toString() {
			if (modifier != null) {
				return modifier.toString();
			} else {
				return contextSensitiveModifier.toString();
			}
		}

		public void apply(BiomeSelectionContext context, BiomeModificationContextImpl modificationContext) {
			if (contextSensitiveModifier != null) {
				contextSensitiveModifier.accept(context, modificationContext);
			} else {
				modifier.accept(modificationContext);
			}
		}

		public void setOrder(int order) {
			this.order = order;
		}
	}
}
