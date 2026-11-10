package io.github.cmdralph.fabricated.forgetest.mixin;

import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code BiomeGenerationSettings.featureSet}, final in vanilla, set without {@code @Mutable} as BCLib sets it: on Fabric
 * Fabric API's biome module's widener makes it mutable. For {@code ForgeWidenedAccessTest}.
 */
@Mixin(BiomeGenerationSettings.class)
public interface BiomeGenerationSettingsAccessor {
	@Accessor("featureSet")
	Supplier<Set<PlacedFeature>> fabricatedtest$featureSet();

	@Accessor("featureSet")
	void fabricatedtest$setFeatureSet(Supplier<Set<PlacedFeature>> featureSet);
}
