package net.fabricmc.fabric.api.event;

/**
 * Stand-in for the part of Fabric API 0.92.12's fabric-api-base the forge-legacy test mod uses (an event's listeners),
 * which Fabricated's native module provides at runtime. Signatures as upstream's at that tag.
 */
public abstract class Event<T> {
	public final T invoker() {
		throw new AssertionError("stand-in");
	}

	public abstract void register(T listener);
}
