package io.github.cmdralph.fabricated.testmod;

import net.neoforged.neoforge.fluids.FluidType;

/** The fluid type calls {@link FluidTest} makes, as NeoForge 21.11 and later name them (1.21.1 has its own). */
final class FluidTypeCompat {
	private FluidTypeCompat() {
	}

	static boolean waterLike(FluidType type) {
		return type.getIsWaterLike();
	}
}
