package net.minecraftforge.common.capabilities;

import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/** {@code ForgeCapabilities}. */
public final class ForgeCapabilities {
	public static Capability<IItemHandler> ITEM_HANDLER;
	public static Capability<IFluidHandler> FLUID_HANDLER;

	private ForgeCapabilities() {
	}
}
