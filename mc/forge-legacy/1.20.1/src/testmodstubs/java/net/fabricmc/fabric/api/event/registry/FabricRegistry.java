package net.fabricmc.fabric.api.event.registry;

import net.minecraft.class_2960;

/**
 * Stand-in for fabric-registry-sync-v0's {@code FabricRegistry}, which the forge-legacy test mod uses (signature as
 * upstream's 0.92.12 for 1.20.1, in Intermediary's names): Fabric API's mixin makes every {@code MappedRegistry} one, so
 * the test mod compiles against this and runs against Fabric API's jar. Never packaged.
 */
public interface FabricRegistry {
	default void addAlias(class_2960 old, class_2960 newId) {
		throw new UnsupportedOperationException("implemented via mixin");
	}
}
