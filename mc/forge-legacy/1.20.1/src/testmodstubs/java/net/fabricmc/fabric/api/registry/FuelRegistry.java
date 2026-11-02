package net.fabricmc.fabric.api.registry;

import net.fabricmc.fabric.api.util.Item2ObjectMap;

/** Stand-in for Fabric API 0.92.12's fabric-content-registries-v0 interface, passed through. */
public interface FuelRegistry extends Item2ObjectMap<Integer> {
	FuelRegistry INSTANCE = null;
}
