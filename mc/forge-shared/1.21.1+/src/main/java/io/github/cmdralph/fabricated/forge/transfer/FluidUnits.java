package io.github.cmdralph.fabricated.forge.transfer;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * Between Fabric's fluid variants in droplets and Forge's fluid stacks in millibuckets. Fabric's variants carry data
 * components, Forge's stacks a tag: a fluid carrying either is left to its own side ({@link #crosses}).
 */
final class FluidUnits {
	/** Fabric's droplets in one of Forge's millibuckets: a bucket is 81000 droplets, or 1000 millibuckets. */
	static final long DROPLETS = 81;

	private FluidUnits() {
	}

	/** The whole millibuckets in {@code droplets}. */
	static int millibuckets(long droplets) {
		return (int) Math.min(droplets / DROPLETS, Integer.MAX_VALUE);
	}

	/** The stack's fluid as a variant: its source fluid, as Fabric's variants never hold a flowing one; not its tag. */
	static FluidVariant variant(FluidStack stack) {
		if (stack.isEmpty()) return FluidVariant.blank();
		Fluid fluid = stack.getFluid();
		if (fluid instanceof FlowingFluid flowing && !fluid.isSource(fluid.defaultFluidState())) fluid = flowing.getSource();
		return FluidVariant.of(fluid);
	}

	/** The variant's fluid as a stack; not its components. */
	static FluidStack stack(FluidVariant variant, int millibuckets) {
		return variant.isBlank() || millibuckets <= 0 ? FluidStack.EMPTY : new FluidStack(variant.getFluid(), millibuckets);
	}

	/** Whether the stack's fluid moves to Fabric's side as it is: it carries no tag. */
	static boolean crosses(FluidStack stack) {
		return stack.getTag() == null || stack.getTag().isEmpty();
	}

	/** Whether the variant's fluid moves to Forge's side as it is: it carries no components. */
	static boolean crosses(FluidVariant variant) {
		return !variant.hasComponents();
	}
}
