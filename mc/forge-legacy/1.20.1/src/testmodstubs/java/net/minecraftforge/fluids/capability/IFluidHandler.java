package net.minecraftforge.fluids.capability;

import net.minecraftforge.fluids.FluidStack;

/** {@code IFluidHandler}, with its {@code FluidAction}: all its methods, as the test mod implements it for an item. */
public interface IFluidHandler {
	int getTanks();

	FluidStack getFluidInTank(int tank);

	int getTankCapacity(int tank);

	boolean isFluidValid(int tank, FluidStack stack);

	int fill(FluidStack resource, FluidAction action);

	FluidStack drain(FluidStack resource, FluidAction action);

	FluidStack drain(int maxDrain, FluidAction action);

	enum FluidAction {
		EXECUTE,
		SIMULATE;

		public boolean execute() {
			throw new AssertionError("stand-in");
		}
	}
}
