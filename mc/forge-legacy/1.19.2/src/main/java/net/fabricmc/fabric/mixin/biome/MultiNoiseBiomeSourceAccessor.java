package net.fabricmc.fabric.mixin.biome;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

/** A multi-noise biome source's entries, which upstream's access widener opens, for {@code NetherBiomeData} (Fabricated). */
@Mixin(MultiNoiseBiomeSource.class)
public interface MultiNoiseBiomeSourceAccessor {
	@Accessor("parameters")
	@Mutable
	void fabricated$setParameters(Climate.ParameterList<Holder<Biome>> parameters);
}
