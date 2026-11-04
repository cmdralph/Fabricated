package io.github.cmdralph.fabricated.forge.rendering.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;

/**
 * A particle's level, which {@link TerrainParticleSpriteMixin} asks a block's level-aware particle material in: the
 * field is {@code Particle}'s, and Mixin finds a shadow in its target's own fields only.
 */
@Mixin(Particle.class)
public interface ParticleLevelAccessor {
	@Accessor("level")
	ClientLevel fabricated$level();
}
