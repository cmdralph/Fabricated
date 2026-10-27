package net.minecraftforge.fluids.capability;

import net.minecraftforge.fluids.FluidStack;

/** {@code IFluidHandler}, with its {@code FluidAction}. */
public interface IFluidHandler {
	int fill(FluidStack resource, FluidAction action);

	FluidStack drain(int maxDrain, FluidAction action);

	enum FluidAction {
		EXECUTE,
		SIMULATE
	}
}
