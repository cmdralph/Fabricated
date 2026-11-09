package io.github.cmdralph.fabricated.forge.biome;

import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.world.ModifiableBiomeInfo;

/** Forge's biome info with another climate, as its record holds it on Forge 61 (BIOME-3). */
public final class BiomeInfoClimate {
	private BiomeInfoClimate() {
	}

	public static ModifiableBiomeInfo.BiomeInfo withClimate(ModifiableBiomeInfo.BiomeInfo info, Biome.ClimateSettings climate) {
		return new ModifiableBiomeInfo.BiomeInfo(climate, info.effects(), info.generationSettings(), info.mobSpawnSettings());
	}
}
