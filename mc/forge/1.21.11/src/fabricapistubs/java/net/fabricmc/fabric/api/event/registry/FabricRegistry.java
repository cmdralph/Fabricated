package net.fabricmc.fabric.api.event.registry;

import net.minecraft.resources.Identifier;

/**
 * Compile-time stand-in: the member of Fabric API's 1.21.11 interface that the forge test mod calls, in Mojang's names
 * (Fabric API's mixin makes every {@code MappedRegistry} one).
 */
public interface FabricRegistry {
	default void addAlias(Identifier old, Identifier newId) {
		throw new UnsupportedOperationException("implemented via mixin");
	}
}
