package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * The forge test mod's part for fabric-biome-api-v1 (with Fabric API's jar only): a feature it adds to the plains through
 * {@code BiomeModifications.addFeature} must be among the plains' features as the overworld's generator reads them. Once
 * a modification changes a biome's features, upstream rebuilds each generator's {@code featuresPerStep}, a field Forge
 * types as its {@code ClearableLazy} where vanilla's is a {@code Supplier}: written with vanilla's type, it would stop the
 * server as it starts (CORPUS-33). The feature is the nether's small ancient debris ore, which no overworld biome has, so
 * adding it orders no two features otherwise.
 */
public final class ForgeBiomeTest {
	private static final ResourceKey<PlacedFeature> FEATURE = ResourceKey.create(Registries.PLACED_FEATURE, Identifier.withDefaultNamespace("ore_debris_small"));
	private static final GenerationStep.Decoration STEP = GenerationStep.Decoration.UNDERGROUND_DECORATION;
	private static boolean registered;

	private ForgeBiomeTest() {
	}

	/** From the main entrypoint, as Fabric mods add their features; only with Fabric API's biome module. */
	public static void register() {
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.PLAINS), STEP, FEATURE);
		registered = true;
	}

	public static void check(MinecraftServer server) {
		if (!registered) return;
		ChunkGenerator generator = server.overworld().getChunkSource().getGenerator();
		Holder<Biome> plains = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
		boolean added = generator.getBiomeGenerationSettings(plains).features().get(STEP.ordinal()).stream().anyMatch(feature -> feature.is(FEATURE));
		if (added) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST biome modification OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST biome modification FAILED: the overworld's generator doesn't read {} among the plains' {} features",
					FEATURE.identifier(), STEP);
		}
	}
}
