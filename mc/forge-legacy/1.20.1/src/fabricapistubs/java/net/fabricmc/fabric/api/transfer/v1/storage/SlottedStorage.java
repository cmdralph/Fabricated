package net.fabricmc.fabric.api.transfer.v1.storage;

import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;

/**
 * Compile-time stand-in: the members of Fabric API's 1.20.1 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public interface SlottedStorage<T> extends Storage<T> {
	int getSlotCount();

	SingleSlotStorage<T> getSlot(int slot);
}
