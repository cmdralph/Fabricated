package net.fabricmc.fabric.api.transfer.v1.fluid;

import net.minecraft.core.Direction;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public final class FluidStorage {
	public static final BlockApiLookup<Storage<FluidVariant>, Direction> SIDED = null;
	public static final ItemApiLookup<Storage<FluidVariant>, ContainerItemContext> ITEM = null;

	private FluidStorage() {
	}
}
