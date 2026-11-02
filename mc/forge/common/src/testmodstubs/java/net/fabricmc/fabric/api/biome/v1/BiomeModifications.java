package net.fabricmc.fabric.api.biome.v1;

import java.util.function.Predicate;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Stand-in for fabric-biome-api-v1's {@code BiomeModifications}, which the forge test mod uses (signatures as upstream's
 * on 1.21.11, 26.1.2, 26.2 and 26.3): Fabricated passes the module through, so the test mod compiles against this and
 * runs against Fabric API's jar. Never packaged.
 */
public final class BiomeModifications {
	private BiomeModifications() {
	}

	public static void addFeature(Predicate<BiomeSelectionContext> biomeSelector, GenerationStep.Decoration step, ResourceKey<PlacedFeature> placedFeatureKey) {
		throw new AssertionError("stand-in");
	}
}
