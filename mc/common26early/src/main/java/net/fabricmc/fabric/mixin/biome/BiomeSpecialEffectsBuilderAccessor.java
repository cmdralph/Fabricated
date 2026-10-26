package net.fabricmc.fabric.mixin.biome;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.biome.BiomeSpecialEffects;

/**
 * The colour overrides of NeoForge's effects builder, which sets them only to a colour on 26.1.2 and 26.2, for Fabric's
 * biome modifications to set them back to none (Fabricated, BIOME-1).
 */
@Mixin(BiomeSpecialEffects.Builder.class)
public interface BiomeSpecialEffectsBuilderAccessor {
	@Accessor("foliageColorOverride")
	void fabricated$setFoliageColorOverride(Optional<Integer> color);

	@Accessor("dryFoliageColorOverride")
	void fabricated$setDryFoliageColorOverride(Optional<Integer> color);

	@Accessor("grassColorOverride")
	void fabricated$setGrassColorOverride(Optional<Integer> color);
}
