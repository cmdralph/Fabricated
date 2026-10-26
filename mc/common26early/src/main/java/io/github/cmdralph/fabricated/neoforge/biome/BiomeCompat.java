package io.github.cmdralph.fabricated.neoforge.biome;

import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.impl.biome.modification.BiomeModificationImpl;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Native fabric-biome-api-v1 on 26.1.2 and 26.2 (BIOME-1, as on 26.3): Fabric's biome modifications run as a NeoForge
 * biome modifier, which Fabricated's data pack declares
 * ({@code data/fabricated/neoforge/biome_modifier/fabric_biome_api.json}); NeoForge applies it to each biome as the
 * server is about to start, phase by phase, as it does its own. Fabric's phases map onto NeoForge's: additions,
 * removals, replacements, then post-processing after everything.
 */
public final class BiomeCompat {
	private BiomeCompat() {
	}

	public static void register(IEventBus modBus) {
		modBus.addListener(RegisterEvent.class, event -> event.register(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS,
				helper -> helper.register(Identifier.fromNamespaceAndPath("fabricated", "fabric_biome_api"), FabricBiomeModifier.CODEC)));
	}

	private enum FabricBiomeModifier implements BiomeModifier {
		INSTANCE;

		static final MapCodec<FabricBiomeModifier> CODEC = MapCodec.unit(INSTANCE);

		@Override
		public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
			ModificationPhase fabricPhase = switch (phase) {
				case BEFORE_EVERYTHING -> null;
				case ADD -> ModificationPhase.ADDITIONS;
				case REMOVE -> ModificationPhase.REMOVALS;
				case MODIFY -> ModificationPhase.REPLACEMENTS;
				case AFTER_EVERYTHING -> ModificationPhase.POST_PROCESSING;
			};
			// NeoForge runs its biome modifiers for the server it has just made current (its handleServerAboutToStart);
			// a modifier isn't handed the registries before 26.3.
			if (fabricPhase != null) {
				BiomeModificationImpl.INSTANCE.modify(ServerLifecycleHooks.getCurrentServer().registryAccess(), biome, fabricPhase, builder);
			}
		}

		@Override
		public MapCodec<? extends BiomeModifier> codec() {
			return CODEC;
		}
	}
}
