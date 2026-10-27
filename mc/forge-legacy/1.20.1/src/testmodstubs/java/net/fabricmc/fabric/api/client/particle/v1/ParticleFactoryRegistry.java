package net.fabricmc.fabric.api.client.particle.v1;

import net.minecraft.class_2394;
import net.minecraft.class_2396;
import net.minecraft.class_707;

public interface ParticleFactoryRegistry {
	static ParticleFactoryRegistry getInstance() {
		throw new AssertionError("stand-in");
	}

	<T extends class_2394> void register(class_2396<T> type, PendingParticleFactory<T> constructor);

	@FunctionalInterface
	interface PendingParticleFactory<T extends class_2394> {
		class_707<T> create(FabricSpriteProvider provider);
	}
}
