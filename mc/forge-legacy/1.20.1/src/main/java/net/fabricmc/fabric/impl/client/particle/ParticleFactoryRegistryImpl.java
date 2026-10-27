/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.impl.client.particle;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

public final class ParticleFactoryRegistryImpl implements ParticleFactoryRegistry {
	public static final ParticleFactoryRegistryImpl INSTANCE = new ParticleFactoryRegistryImpl();

	static class DeferredParticleFactoryRegistry implements ParticleFactoryRegistry {
		private final Map<ParticleType<?>, ParticleProvider<?>> factories = new IdentityHashMap<>();
		private final Map<ParticleType<?>, PendingParticleFactory<?>> constructors = new IdentityHashMap<>();

		@Override
		public <T extends ParticleOptions> void register(ParticleType<T> type, ParticleProvider<T> factory) {
			factories.put(type, factory);
		}

		@Override
		public <T extends ParticleOptions> void register(ParticleType<T> type, PendingParticleFactory<T> factory) {
			constructors.put(type, factory);
		}

		@SuppressWarnings("unchecked")
		void applyTo(ParticleFactoryRegistry registry) {
			for (Map.Entry<ParticleType<?>, ParticleProvider<?>> entry : factories.entrySet()) {
				ParticleType type = entry.getKey();
				ParticleProvider factory = entry.getValue();
				registry.register(type, factory);
			}

			for (Map.Entry<ParticleType<?>, PendingParticleFactory<?>> entry : constructors.entrySet()) {
				ParticleType type = entry.getKey();
				PendingParticleFactory constructor = entry.getValue();
				registry.register(type, constructor);
			}
		}
	}

	/**
	 * Fabricated: upstream puts a factory in the particle engine's map by the type's raw id, and a pending factory's
	 * sprite set in its map of sprite sets, through an accessor. Forge 47 keeps the factories by the type's id instead,
	 * so this registers both through the engine's own methods (public on Forge, which registers its mods' factories
	 * through them too): the sprite set the engine makes there is the one a {@link FabricSpriteProviderImpl} wraps.
	 */
	static class DirectParticleFactoryRegistry implements ParticleFactoryRegistry {
		private final ParticleEngine particleManager;

		DirectParticleFactoryRegistry(ParticleEngine particleManager) {
			this.particleManager = particleManager;
		}

		@Override
		public <T extends ParticleOptions> void register(ParticleType<T> type, ParticleProvider<T> factory) {
			particleManager.register(type, factory);
		}

		@Override
		public <T extends ParticleOptions> void register(ParticleType<T> type, PendingParticleFactory<T> constructor) {
			particleManager.register(type, delegate -> constructor.create(new FabricSpriteProviderImpl(particleManager, delegate)));
		}
	}

	ParticleFactoryRegistry internalRegistry = new DeferredParticleFactoryRegistry();

	private ParticleFactoryRegistryImpl() { }

	@Override
	public <T extends ParticleOptions> void register(ParticleType<T> type, ParticleProvider<T> factory) {
		internalRegistry.register(type, factory);
	}

	@Override
	public <T extends ParticleOptions> void register(ParticleType<T> type, PendingParticleFactory<T> constructor) {
		internalRegistry.register(type, constructor);
	}

	public void initialize(ParticleEngine particleManager) {
		ParticleFactoryRegistry newRegistry = new DirectParticleFactoryRegistry(particleManager);
		DeferredParticleFactoryRegistry oldRegistry = (DeferredParticleFactoryRegistry) internalRegistry;
		oldRegistry.applyTo(newRegistry);
		internalRegistry = newRegistry;
	}
}
