package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import io.github.cmdralph.fabricated.neoforge.particle.FabricParticleProviders;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.resources.Identifier;

/**
 * Adds back vanilla's {@code providers} field (an {@code Int2ObjectMap}, next to NeoForge's map of the same name),
 * which {@code fabric-particles-v1} registers into; see {@link FabricParticleProviders}. Adds a field only.
 */
@Mixin(ParticleResources.class)
public abstract class ParticleResourcesMixin {
	public final Int2ObjectMap<ParticleProvider<?>> providers = new FabricParticleProviders(this::getProviders);

	@Shadow
	public abstract Map<Identifier, ParticleProvider<?>> getProviders();
}
