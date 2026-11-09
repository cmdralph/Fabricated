package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraftforge.common.world.ModifiableBiomeInfo;

/** Forge's biome info, where a Fabric mod's weather modification goes ({@link BiomeWeatherWriteMixin}). */
@Mixin(value = ModifiableBiomeInfo.class, remap = false)
public interface ModifiableBiomeInfoAccessor {
	@Accessor("originalBiomeInfo")
	@Mutable
	void fabricated$setOriginalBiomeInfo(ModifiableBiomeInfo.BiomeInfo info);

	@Accessor("modifiedBiomeInfo")
	void fabricated$setModifiedBiomeInfo(ModifiableBiomeInfo.BiomeInfo info);
}
