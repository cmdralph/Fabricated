package net.fabricmc.fabric.mixin.biome;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.core.Holder;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.biome.AmbientAdditionsSettings;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.AmbientParticleSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;

/**
 * The optional parts of Forge's effects builder, which its methods set only to a value, for Fabric's biome
 * modifications to set them back to none as well (Fabricated, L4 on Forge 47). Forge's access transformer makes these
 * fields protected for its own builder; the names are those its comments give.
 */
@Mixin(BiomeSpecialEffects.Builder.class)
public interface BiomeSpecialEffectsBuilderAccessor {
	@Accessor("foliageColorOverride")
	void fabricated$setFoliageColorOverride(Optional<Integer> color);

	@Accessor("grassColorOverride")
	void fabricated$setGrassColorOverride(Optional<Integer> color);

	@Accessor("ambientParticle")
	void fabricated$setAmbientParticle(Optional<AmbientParticleSettings> particle);

	@Accessor("ambientLoopSoundEvent")
	void fabricated$setAmbientLoopSoundEvent(Optional<Holder<SoundEvent>> sound);

	@Accessor("ambientMoodSettings")
	void fabricated$setAmbientMoodSettings(Optional<AmbientMoodSettings> sound);

	@Accessor("ambientAdditionsSettings")
	void fabricated$setAmbientAdditionsSettings(Optional<AmbientAdditionsSettings> sound);

	@Accessor("backgroundMusic")
	void fabricated$setBackgroundMusic(Optional<Music> music);
}
