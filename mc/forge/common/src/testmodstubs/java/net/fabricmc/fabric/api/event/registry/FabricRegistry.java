package net.fabricmc.fabric.api.event.registry;

import net.minecraft.resources.Identifier;

/**
 * Stand-in for fabric-registry-sync-v0's {@code FabricRegistry}, which the forge test mod uses (signatures as upstream's
 * on 1.21.11, 26.1.2, 26.2 and 26.3): Fabric API's mixin makes every {@code MappedRegistry} one, so the test mod
 * compiles against this and runs against Fabric API's jar. Never packaged.
 */
public interface FabricRegistry {
	default void addAlias(Identifier old, Identifier newId) {
		throw new UnsupportedOperationException("implemented via mixin");
	}
}
