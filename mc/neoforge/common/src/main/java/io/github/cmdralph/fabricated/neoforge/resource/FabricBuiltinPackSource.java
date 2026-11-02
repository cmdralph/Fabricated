package io.github.cmdralph.fabricated.neoforge.resource;

import net.minecraft.server.packs.repository.PackSource;

/**
 * The pack source of a built-in pack a Fabric mod registers, on every version; its {@link #shouldAddAutomatically()} is
 * the pack's activation type's {@code isEnabledByDefault()}. Each version's source (under the class name that version's
 * Fabric API modules test for) implements it, so {@link DefaultResourcePacks} can tell those packs from others.
 */
public interface FabricBuiltinPackSource extends PackSource {
}
