package net.fabricmc.fabric.mixin.biome;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.level.biome.Biome;

/**
 * A biome's environment attributes, which NeoForge's biome info doesn't hold on 1.21.11: Fabric's biome
 * modifications replace them on the biome, as upstream's do (Fabricated, BIOME-1).
 */
@Mixin(Biome.class)
public interface BiomeAccessor {
	@Accessor("attributes")
	@Mutable
	void fabricated$setAttributes(EnvironmentAttributeMap attributes);
}
