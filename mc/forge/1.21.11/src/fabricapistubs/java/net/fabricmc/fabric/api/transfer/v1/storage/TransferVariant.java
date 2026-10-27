package net.fabricmc.fabric.api.transfer.v1.storage;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public interface TransferVariant<O> {
	boolean isBlank();

	O getObject();

	default boolean hasComponents() {
		throw new AssertionError("stand-in");
	}
}
