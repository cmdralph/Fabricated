package net.fabricmc.fabric.mixin.biome;

import java.util.Set;
import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;

/**
 * The lazy supplier of a biome source's possible biomes that Forge 43 keeps in place of vanilla's set, for
 * {@code NetherBiomeData} to replace with one of the modded Nether's biomes (Fabricated). A field of Forge's: not remapped.
 */
@Mixin(BiomeSource.class)
public interface BiomeSourceAccessor {
	@Accessor(value = "lazyPossibleBiomes", remap = false)
	@Mutable
	void fabricated$setLazyPossibleBiomes(Supplier<Set<Holder<Biome>>> biomes);
}
