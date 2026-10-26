package net.fabricmc.fabric.api.biome.v1;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class BiomeModification {
	private BiomeModification() {
	}

	public BiomeModification add(ModificationPhase phase, Predicate<BiomeSelectionContext> selector, Consumer<BiomeModificationContext> modifier) {
		throw new AssertionError("stand-in");
	}
}
