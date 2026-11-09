package io.github.cmdralph.fabricated.testmod;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.fluids.FluidType;

/** The fluid type calls {@link FluidTest} makes, as NeoForge 21.1 names them (1.21.11 and later have their own). */
final class FluidTypeCompat {
	private FluidTypeCompat() {
	}

	/** Water to the fluid's type: Fabricated's fluid types answer these all alike (FabricFluidType). */
	static boolean waterLike(FluidType type) {
		return type.canExtinguish((Entity) null);
	}
}
