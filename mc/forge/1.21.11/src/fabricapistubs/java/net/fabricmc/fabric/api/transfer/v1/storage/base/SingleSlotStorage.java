package net.fabricmc.fabric.api.transfer.v1.storage.base;

import java.util.Iterator;

import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public interface SingleSlotStorage<T> extends SlottedStorage<T>, StorageView<T> {
	@Override
	default Iterator<StorageView<T>> iterator() {
		throw new AssertionError("stand-in");
	}

	@Override
	default int getSlotCount() {
		throw new AssertionError("stand-in");
	}

	@Override
	default SingleSlotStorage<T> getSlot(int slot) {
		throw new AssertionError("stand-in");
	}
}
