package net.fabricmc.fabric.api.biome.v1;

import java.util.function.Predicate;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/** Stand-in for fabric-biome-api-v1's {@code BiomeSelectors} (see {@link BiomeModifications}). Never packaged. */
public final class BiomeSelectors {
	private BiomeSelectors() {
	}

	@SafeVarargs
	public static Predicate<BiomeSelectionContext> includeByKey(ResourceKey<Biome>... keys) {
		throw new AssertionError("stand-in");
	}
}
