package io.github.cmdralph.fabricated.neoforge.particle;

import java.util.Map;
import java.util.function.Supplier;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * The particle provider map as vanilla had it, by raw particle type id, for {@code fabric-particles-v1}, which
 * registers providers into it directly. NeoForge keys the map by particle type id instead; registrations made here
 * go into NeoForge's map. Only registering is supported, the one thing upstream does with it.
 */
public final class FabricParticleProviders extends Int2ObjectOpenHashMap<ParticleProvider<?>> {
	private static final long serialVersionUID = 1L;
	private final transient Supplier<Map<Identifier, ParticleProvider<?>>> neoForge;

	public FabricParticleProviders(Supplier<Map<Identifier, ParticleProvider<?>>> neoForge) {
		this.neoForge = neoForge;
	}

	@Override
	public ParticleProvider<?> put(int rawId, ParticleProvider<?> provider) {
		Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(BuiltInRegistries.PARTICLE_TYPE.byId(rawId));
		if (id == null) throw new IllegalArgumentException("No particle type with raw id " + rawId);
		return neoForge.get().put(id, provider);
	}
}
