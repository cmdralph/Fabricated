package net.fabricmc.fabric.api.entity.event.v1.effect;

import org.jspecify.annotations.Nullable;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 interface that Fabricated's in-game mod and the forge test
 * mod call.
 */
public interface EffectEventContext {
	boolean isFromCommand();

	@Nullable String commandName();
}
