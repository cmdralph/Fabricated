package net.fabricmc.fabric.api.transfer.v1.item;

import net.minecraft.class_1792;
import net.minecraft.class_1935;

import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;

public interface ItemVariant extends TransferVariant<class_1792> {
	static ItemVariant blank() {
		throw new AssertionError("stand-in");
	}

	static ItemVariant of(class_1935 item) {
		throw new AssertionError("stand-in");
	}
}
