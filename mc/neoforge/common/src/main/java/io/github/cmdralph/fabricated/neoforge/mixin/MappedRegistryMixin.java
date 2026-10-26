package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.fabricmc.fabric.api.event.registry.FabricRegistry;
import net.minecraft.core.MappedRegistry;

/**
 * Makes registries Fabric's {@link FabricRegistry}. Its only method, {@code addAlias(Identifier, Identifier)}, is
 * NeoForge's own registry alias method, which the registry already has, so nothing else is added and no Minecraft
 * code is changed.
 */
@Mixin(MappedRegistry.class)
abstract class MappedRegistryMixin implements FabricRegistry {
}
