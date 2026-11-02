package net.fabricmc.fabric.impl.content.registry;

import java.util.Map;

import net.minecraft.world.item.Item;

import net.fabricmc.fabric.api.registry.FuelRegistry;

/**
 * Compile-time stand-in: the members of Fabric API's 1.20.1 implementation that Fabricated's in-game mod calls, in
 * Mojang's names.
 */
public final class FuelRegistryImpl implements FuelRegistry {
	private FuelRegistryImpl() {
	}

	/** Adds the registry's fuels to a fuel table, and removes the ones it removed. */
	public void apply(Map<Item, Integer> map) {
		throw new AssertionError("stand-in");
	}
}
