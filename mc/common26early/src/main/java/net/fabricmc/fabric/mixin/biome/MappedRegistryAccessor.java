package net.fabricmc.fabric.mixin.biome;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;

/**
 * A registry's registration info, whose known pack a biome whose environment attributes Fabric's modifications changed
 * loses, so that clients are sent the biome rather than read their own copy (Fabricated, BIOME-1; NeoForge does the
 * same for the biomes its modifiers change, and upstream for every biome it modifies).
 */
@Mixin(MappedRegistry.class)
public interface MappedRegistryAccessor<T> {
	@Accessor("registrationInfos")
	Map<ResourceKey<T>, RegistrationInfo> fabricated$getRegistrationInfos();
}
