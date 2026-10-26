package net.fabricmc.fabric.api.biome.v1;

import java.util.function.Predicate;

import net.minecraft.class_1959;
import net.minecraft.class_5321;

public final class BiomeSelectors {
	private BiomeSelectors() {
	}

	@SafeVarargs
	public static Predicate<BiomeSelectionContext> includeByKey(class_5321<class_1959>... keys) {
		throw new AssertionError("stand-in");
	}
}
