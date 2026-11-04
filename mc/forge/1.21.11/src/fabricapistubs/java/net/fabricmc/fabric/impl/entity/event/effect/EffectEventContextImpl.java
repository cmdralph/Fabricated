package net.fabricmc.fabric.impl.entity.event.effect;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.entity.event.v1.effect.EffectEventContext;

/**
 * Compile-time stand-in: Fabric API's 1.21.11 record, which Fabricated's in-game mod creates.
 */
public record EffectEventContextImpl(boolean isFromCommand, @Nullable String commandName) implements EffectEventContext {
}
