package io.github.cmdralph.fabricated.forge.particle;

import java.util.Iterator;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.AbstractInt2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Forge 47's particle providers, kept by their type's id, as vanilla's map by the type's raw id (CORPUS-37), which
 * Porting Lib's accessor of the particle engine's providers reads. Reads and writes go to Forge's map: a raw id is
 * the particle type registry's, and an id no type has gets nothing, and can't be written. An entry's key is its
 * type's raw id, or -1 for an id the registry has no type of.
 */
public final class ParticleProvidersById extends AbstractInt2ObjectMap<ParticleProvider<?>> {
	private static final long serialVersionUID = 1L;

	private final transient Map<ResourceLocation, ParticleProvider<?>> byId;

	/** @param byId Forge's map, the particle engine's own */
	public ParticleProvidersById(Map<ResourceLocation, ParticleProvider<?>> byId) {
		this.byId = byId;
	}

	/** The id of the type with this raw id, or null when the registry has none. */
	private static ResourceLocation id(int rawId) {
		ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.byId(rawId);
		return type == null ? null : BuiltInRegistries.PARTICLE_TYPE.getKey(type);
	}

	private static int rawId(ResourceLocation id) {
		ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(id);
		return type == null ? -1 : BuiltInRegistries.PARTICLE_TYPE.getId(type);
	}

	@Override
	public int size() {
		return byId.size();
	}

	@Override
	public boolean containsKey(int rawId) {
		ResourceLocation id = id(rawId);
		return id != null && byId.containsKey(id);
	}

	@Override
	public boolean containsValue(Object provider) {
		return byId.containsValue(provider);
	}

	@Override
	public ParticleProvider<?> get(int rawId) {
		ResourceLocation id = id(rawId);
		ParticleProvider<?> provider = id == null ? null : byId.get(id);
		return provider == null ? defaultReturnValue() : provider;
	}

	@Override
	public ParticleProvider<?> put(int rawId, ParticleProvider<?> provider) {
		ResourceLocation id = id(rawId);
		if (id == null) throw new IllegalArgumentException("No particle type has the raw id " + rawId);
		ParticleProvider<?> previous = byId.put(id, provider);
		return previous == null ? defaultReturnValue() : previous;
	}

	@Override
	public ParticleProvider<?> remove(int rawId) {
		ResourceLocation id = id(rawId);
		ParticleProvider<?> previous = id == null ? null : byId.remove(id);
		return previous == null ? defaultReturnValue() : previous;
	}

	@Override
	public void clear() {
		byId.clear();
	}

	@Override
	public ObjectSet<Int2ObjectMap.Entry<ParticleProvider<?>>> int2ObjectEntrySet() {
		return new AbstractObjectSet<>() {
			@Override
			public int size() {
				return byId.size();
			}

			@Override
			public ObjectIterator<Int2ObjectMap.Entry<ParticleProvider<?>>> iterator() {
				Iterator<Map.Entry<ResourceLocation, ParticleProvider<?>>> entries = byId.entrySet().iterator();
				return new ObjectIterator<>() {
					@Override
					public boolean hasNext() {
						return entries.hasNext();
					}

					@Override
					public Int2ObjectMap.Entry<ParticleProvider<?>> next() {
						Map.Entry<ResourceLocation, ParticleProvider<?>> entry = entries.next();
						return new BasicEntry<>(rawId(entry.getKey()), entry.getValue());
					}

					@Override
					public void remove() {
						entries.remove();
					}
				};
			}
		};
	}
}
