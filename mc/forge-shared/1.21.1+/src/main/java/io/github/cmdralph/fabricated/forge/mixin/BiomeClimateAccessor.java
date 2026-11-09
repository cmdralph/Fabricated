package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.biome.Biome;

/** The biome's climate field, which Forge keeps private and final ({@link BiomeWeatherWriteMixin}). */
@Mixin(Biome.class)
public interface BiomeClimateAccessor {
	@Accessor("climateSettings")
	@Mutable
	void fabricated$setClimateSettings(Biome.ClimateSettings climate);
}
