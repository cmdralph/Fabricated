package net.minecraftforge.fluids.capability.templates;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** {@code FluidTank}. */
public class FluidTank implements IFluidHandler {
	public FluidTank(int capacity) {
		throw new AssertionError("stand-in");
	}

	public int getFluidAmount() {
		throw new AssertionError("stand-in");
	}

	@Override
	public int getTanks() {
		throw new AssertionError("stand-in");
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		throw new AssertionError("stand-in");
	}

	@Override
	public int getTankCapacity(int tank) {
		throw new AssertionError("stand-in");
	}

	@Override
	public boolean isFluidValid(int tank, FluidStack stack) {
		throw new AssertionError("stand-in");
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		throw new AssertionError("stand-in");
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		throw new AssertionError("stand-in");
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		throw new AssertionError("stand-in");
	}
}
