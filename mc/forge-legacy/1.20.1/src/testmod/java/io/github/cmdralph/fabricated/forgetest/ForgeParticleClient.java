package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.class_1059;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_727;

/**
 * The client side of {@link ForgeParticleTest}: a factory for the test mod's particle type that takes a sprite
 * provider, registered from the client entrypoint as Fabric mods register theirs (on Forge 47 that runs before
 * {@code Minecraft}'s constructor makes the particle engine, which the check's line says). Once the game is up, the
 * particle engine must find the factory when a particle of the type is made ({@code ParticleEngine.createParticle},
 * {@code class_702.method_3056}), and the sprite provider must hold the one sprite the test mod's
 * {@code particles/sparkle.json} lists, in the particle atlas. Loading {@code TerrainParticle} ({@code class_727})
 * applies the module's block dust tint hook, which keeps it from loading if the hook doesn't apply.
 */
@Environment(EnvType.CLIENT)
public final class ForgeParticleClient {
	private static final class_2960 PARTICLE_ATLAS = new class_2960("minecraft", "textures/atlas/particles.png");
	private static volatile FabricSpriteProvider sprites;
	private static volatile boolean asked;
	/** Whether {@link #register} ran before the particle engine existed ({@code field_1713} is {@code particleEngine}). */
	private static boolean beforeEngine;

	private ForgeParticleClient() {
	}

	/** From the client entrypoint, after the main one registered the type. The factory makes no particle. */
	static void register() {
		beforeEngine = class_310.method_1551().field_1713 == null;
		ParticleFactoryRegistry.getInstance().register(ForgeParticleTest.sparkle, provider -> {
			sprites = provider;
			return (options, level, x, y, z, xSpeed, ySpeed, zSpeed) -> {
				asked = true;
				return null;
			};
		});
	}

	/** After the first tick of the client's integrated server, when the particle engine has loaded its sprites. */
	static void check() {
		List<String> failed = new ArrayList<>();
		class_310.method_1551().field_1713.method_3056(ForgeParticleTest.sparkle, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
		if (!asked) failed.add("the particle engine didn't ask the factory for a particle");
		FabricSpriteProvider provider = sprites;
		if (provider == null) {
			failed.add("the factory got no sprite provider");
		} else {
			int count = provider.getSprites().size();
			if (count != 1) failed.add("the sprite provider has " + count + " sprites, not sparkle.json's 1");
			class_1059 atlas = provider.getAtlas();
			if (atlas == null || !PARTICLE_ATLAS.equals(atlas.method_24106())) {
				failed.add("the sprite provider's atlas is " + (atlas == null ? null : atlas.method_24106()));
			}
		}
		// A tint hook that doesn't apply fails this load, which the caller logs.
		ForgeTestMod.LOGGER.debug("TerrainParticle is {} here", class_727.class.getName());
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST particles OK (factory registered {} the particle engine existed)",
					beforeEngine ? "before" : "after");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST particles: {}", String.join("; ", failed));
		}
	}
}
