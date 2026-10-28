package io.github.cmdralph.fabricated.neoforge.biome;

import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.impl.biome.modification.BiomeModificationImpl;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Native fabric-biome-api-v1 on 26.3 (BIOME-1): Fabric's biome modifications run as a NeoForge biome modifier, which
 * Fabricated's data pack declares ({@code data/fabricated/neoforge/biome_modifier/fabric_biome_api.json}); NeoForge
 * applies it to each biome as the server starts, phase by phase, as it does its own. Fabric's phases map onto
 * NeoForge's: additions, removals, replacements, then post-processing after everything.
 */
public final class BiomeCompat {
	/** False when another mod provides fabric-biome-api-v1, and applies Fabric's modifications itself. */
	private static volatile boolean modifies;

	private BiomeCompat() {
	}

	/**
	 * @param modifies whether the modifier applies Fabric's modifications; it is registered either way, as Fabricated's
	 *                 data pack declares it
	 */
	public static void register(IEventBus modBus, boolean modifies) {
		BiomeCompat.modifies = modifies;
		modBus.addListener(RegisterEvent.class, event -> event.register(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS,
				helper -> helper.register(Identifier.fromNamespaceAndPath("fabricated", "fabric_biome_api"), FabricBiomeModifier.CODEC)));
	}

	private enum FabricBiomeModifier implements BiomeModifier {
		INSTANCE;

		static final MapCodec<FabricBiomeModifier> CODEC = MapCodec.unit(INSTANCE);

		@Override
		public void modify(RegistryAccess registries, Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
			ModificationPhase fabricPhase = switch (phase) {
				case BEFORE_EVERYTHING -> null;
				case ADD -> ModificationPhase.ADDITIONS;
				case REMOVE -> ModificationPhase.REMOVALS;
				case MODIFY -> ModificationPhase.REPLACEMENTS;
				case AFTER_EVERYTHING -> ModificationPhase.POST_PROCESSING;
			};
			if (fabricPhase != null && modifies) BiomeModificationImpl.INSTANCE.modify(registries, biome, fabricPhase, builder);
		}

		@Override
		public MapCodec<? extends BiomeModifier> codec() {
			return CODEC;
		}
	}
}
