package io.github.cmdralph.fabricated.forgetest;

import java.util.Set;
import java.util.function.Supplier;

import io.github.cmdralph.fabricated.forgetest.mixin.BiomeGenerationSettingsAccessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * The forge-legacy test mod's part for what upstream's wideners of the Fabric API modules Fabricated ships natively on
 * Forge 47 open, which the in-game mod's access transformer opens (BIOME-6): a Fabric mod's accessor sets a final field
 * Fabric API's biome module makes mutable, without {@code @Mutable}, as BCLib does. Setting plains' feature set to the
 * one it holds must not throw, and the field must hold it.
 */
public final class ForgeWidenedAccessTest {
	private ForgeWidenedAccessTest() {
	}

	/** At the first server tick. */
	public static void check(ServerLevel level) {
		BiomeGenerationSettings settings = level.registryAccess().registryOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS).getGenerationSettings();
		BiomeGenerationSettingsAccessor accessor = (BiomeGenerationSettingsAccessor) settings;
		Supplier<Set<PlacedFeature>> featureSet = accessor.fabricatedtest$featureSet();
		accessor.fabricatedtest$setFeatureSet(featureSet);
		if (accessor.fabricatedtest$featureSet() == featureSet) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST widened access OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST widened access FAILED: the feature set set is not the one read back");
		}
	}
}
