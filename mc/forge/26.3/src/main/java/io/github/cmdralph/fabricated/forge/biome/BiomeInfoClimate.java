package io.github.cmdralph.fabricated.forge.biome;

import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.world.ModifiableBiomeInfo;

/** Forge's biome info with another climate, as its record holds it on Forge 66: with the environment attributes (BIOME-3). */
public final class BiomeInfoClimate {
	private BiomeInfoClimate() {
	}

	public static ModifiableBiomeInfo.BiomeInfo withClimate(ModifiableBiomeInfo.BiomeInfo info, Biome.ClimateSettings climate) {
		return new ModifiableBiomeInfo.BiomeInfo(climate, info.attributes(), info.effects(), info.generationSettings());
	}
}
