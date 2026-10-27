package net.minecraftforge.common.capabilities;

import net.minecraft.class_2350;

import net.minecraftforge.common.util.LazyOptional;

/** {@code ICapabilityProvider}. */
public interface ICapabilityProvider {
	<T> LazyOptional<T> getCapability(Capability<T> capability, class_2350 side);
}
