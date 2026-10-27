package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.class_2378;
import net.minecraft.class_2400;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

/**
 * The forge-legacy test mod's part for Fabricated's native fabric-particles-v1 on Forge 47 (PART-1): Forge keeps the
 * particle engine's factories by the type's id, where upstream's accessor read them by raw id. The main entrypoint
 * registers a particle type that {@code FabricParticleTypes.simple()} makes, as Fabric mods do, on both sides; the
 * client registers its factory and checks it ({@link ForgeParticleClient}).
 */
public final class ForgeParticleTest {
	public static final String PATH = "sparkle";
	/** The particle type {@link #register} registers ({@code SimpleParticleType}). */
	static class_2400 sparkle;

	private ForgeParticleTest() {
	}

	/** From the main entrypoint. */
	static void register() {
		sparkle = class_2378.method_10230(class_7923.field_41180, new class_2960(ForgeTestMod.MOD_ID, PATH), FabricParticleTypes.simple());
	}
}
