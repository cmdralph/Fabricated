package io.github.cmdralph.fabricated.testmod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.21.1 part of the CI test mod for Fabricated's native fabric-particles-v1 (PART-1 on NeoForge 21.1): NeoForge keeps
 * the particle engine's factories by the type's id, where upstream's accessor read them by raw id. A particle type
 * that {@code FabricParticleTypes.simple()} makes is registered on both sides, as Fabric mods do (from
 * {@link ObjectBuilderTest}'s main entrypoint); the client registers its factory and checks it
 * ({@link ParticleClientTest}).
 */
public final class ParticleTest {
	static SimpleParticleType sparkle;

	private ParticleTest() {
	}

	static void register() {
		sparkle = Registry.register(BuiltInRegistries.PARTICLE_TYPE, ResourceLocation.fromNamespaceAndPath("fabricated_testmod", "sparkle"),
				FabricParticleTypes.simple());
	}
}
