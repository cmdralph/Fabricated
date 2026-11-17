package net.fabricmc.fabric.mixin.biome;

import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

/** A multi-noise preset's entries, which upstream's access widener opens, for {@code NetherBiomeData} (Fabricated). */
@Mixin(MultiNoiseBiomeSource.Preset.class)
public interface MultiNoiseBiomeSourcePresetAccessor {
	@Accessor("parameterSource")
	Function<Registry<Biome>, Climate.ParameterList<Holder<Biome>>> fabricated$getParameterSource();
}
