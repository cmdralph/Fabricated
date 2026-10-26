package io.github.cmdralph.fabricated.forge.biome;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.impl.biome.modification.BiomeModificationImpl;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Native fabric-biome-api-v1 on Forge 47 (L4, as BIOME-1 on NeoForge): Fabric's biome modifications run as a Forge
 * biome modifier, which the in-game mod's data pack declares
 * ({@code data/fabricated/forge/biome_modifier/fabric_biome_api.json}); Forge applies it to each biome as the server is
 * about to start, phase by phase, as it does its own. Fabric's phases map onto Forge's: additions, removals,
 * replacements, then post-processing after everything.
 *
 * <p>Upstream's module, passed through, widened the biome's climate field with its access widener, so Forge's coremod,
 * which points reads of the climate and effects fields at the biome's modified info and requires both private, failed
 * as the biome class loaded: no biome modifier's climate or effects changed what the game read, Forge's own included.
 */
public final class BiomeCompat {
	private BiomeCompat() {
	}

	public static void register(IEventBus modBus) {
		modBus.addListener(EventPriority.NORMAL, false, RegisterEvent.class, event -> event.register(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS,
				helper -> helper.register(new ResourceLocation("fabricated", "fabric_biome_api"), FabricBiomeModifier.CODEC)));
	}

	private enum FabricBiomeModifier implements BiomeModifier {
		INSTANCE;

		static final Codec<FabricBiomeModifier> CODEC = Codec.unit(INSTANCE);

		@Override
		public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
			ModificationPhase fabricPhase = switch (phase) {
				case BEFORE_EVERYTHING -> null;
				case ADD -> ModificationPhase.ADDITIONS;
				case REMOVE -> ModificationPhase.REMOVALS;
				case MODIFY -> ModificationPhase.REPLACEMENTS;
				case AFTER_EVERYTHING -> ModificationPhase.POST_PROCESSING;
			};
			// Forge runs its biome modifiers for the server it has just made current (its handleServerAboutToStart);
			// a modifier isn't handed the registries.
			if (fabricPhase != null) {
				BiomeModificationImpl.INSTANCE.modify(ServerLifecycleHooks.getCurrentServer().registryAccess(), biome, fabricPhase, builder);
			}
		}

		@Override
		public Codec<? extends BiomeModifier> codec() {
			return CODEC;
		}
	}
}
