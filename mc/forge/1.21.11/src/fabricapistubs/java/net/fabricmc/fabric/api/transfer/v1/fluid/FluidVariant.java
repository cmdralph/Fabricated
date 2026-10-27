package net.fabricmc.fabric.api.transfer.v1.fluid;

import net.minecraft.world.level.material.Fluid;

import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public interface FluidVariant extends TransferVariant<Fluid> {
	static FluidVariant blank() {
		throw new AssertionError("stand-in");
	}

	static FluidVariant of(Fluid fluid) {
		throw new AssertionError("stand-in");
	}

	default Fluid getFluid() {
		throw new AssertionError("stand-in");
	}
}
