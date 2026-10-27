package net.fabricmc.fabric.api.transfer.v1.item;

import net.minecraft.core.Direction;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public final class ItemStorage {
	public static final BlockApiLookup<Storage<ItemVariant>, Direction> SIDED = null;

	private ItemStorage() {
	}
}
