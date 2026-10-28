package io.github.cmdralph.fabricated.neoforge.transfer;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Between Fabric's fluid variants in droplets and NeoForge 21.1's fluid stacks in millibuckets. Both carry the fluid's
 * data components, which cross as they are.
 */
final class FluidUnits {
	/** Fabric's droplets in one of NeoForge's millibuckets: a bucket is 81000 droplets, or 1000 millibuckets. */
	static final long DROPLETS = 81;

	private FluidUnits() {
	}

	/** The whole millibuckets in {@code droplets}. */
	static int millibuckets(long droplets) {
		return (int) Math.min(droplets / DROPLETS, Integer.MAX_VALUE);
	}

	/** The stack's fluid as a variant: its source fluid, as Fabric's variants never hold a flowing one. */
	static FluidVariant variant(FluidStack stack) {
		if (stack.isEmpty()) return FluidVariant.blank();
		Fluid fluid = stack.getFluid();
		if (fluid instanceof FlowingFluid flowing && !fluid.isSource(fluid.defaultFluidState())) fluid = flowing.getSource();
		return FluidVariant.of(fluid, stack.getComponentsPatch());
	}

	static FluidStack stack(FluidVariant variant, int millibuckets) {
		if (variant.isBlank() || millibuckets <= 0) return FluidStack.EMPTY;
		return new FluidStack(variant.getFluid().builtInRegistryHolder(), millibuckets, variant.getComponents());
	}
}
