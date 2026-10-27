package net.fabricmc.fabric.api.transfer.v1.fluid;

import net.minecraft.class_3611;

import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;

public interface FluidVariant extends TransferVariant<class_3611> {
	static FluidVariant blank() {
		throw new AssertionError("stand-in");
	}

	static FluidVariant of(class_3611 fluid) {
		throw new AssertionError("stand-in");
	}
}
