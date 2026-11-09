package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * The client side of {@link ParticleTest}: a factory for the test mod's particle type that takes a sprite provider,
 * registered from the client entrypoint as Fabric mods register theirs. Once the player is in the world, the particle
 * engine must find the factory when a particle of the type is made, and the sprite provider must hold the one sprite
 * the test mod's {@code particles/sparkle.json} lists, in the particle atlas. Loading {@code TerrainParticle} applies
 * the module's block dust tint hook, which keeps it from loading if the hook doesn't apply.
 */
final class ParticleClientTest {
	private static volatile FabricSpriteProvider sprites;
	private static volatile boolean asked;
	private static boolean checked;

	private ParticleClientTest() {
	}

	/** From the client entrypoint, after the main one registered the type. The factory makes no particle. */
	static void register() {
		ParticleFactoryRegistry.getInstance().register(ParticleTest.sparkle, provider -> {
			sprites = provider;
			return (options, level, x, y, z, xSpeed, ySpeed, zSpeed) -> {
				asked = true;
				return null;
			};
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!checked && client.player != null && client.player.tickCount > 20) {
				checked = true;
				check(client);
			}
		});
	}

	private static void check(Minecraft client) {
		List<String> failed = new ArrayList<>();
		client.particleEngine.createParticle(ParticleTest.sparkle, client.player.getX(), client.player.getY(), client.player.getZ(), 0, 0, 0);
		if (!asked) failed.add("the particle engine didn't ask the factory for a particle");
		FabricSpriteProvider provider = sprites;
		if (provider == null) {
			failed.add("the factory got no sprite provider");
		} else {
			int count = provider.getSprites().size();
			if (count != 1) failed.add("the sprite provider has " + count + " sprites, not sparkle.json's 1");
			TextureAtlas atlas = provider.getAtlas();
			if (atlas == null || !TextureAtlas.LOCATION_PARTICLES.equals(atlas.location())) {
				failed.add("the sprite provider's atlas is " + (atlas == null ? null : atlas.location()));
			}
		}
		try {
			TestMod.LOGGER.debug("TerrainParticle is {} here", TerrainParticle.class.getName());
		} catch (Error e) {
			failed.add("TerrainParticle doesn't load: " + e);
		}
		if (failed.isEmpty()) TestMod.LOGGER.info("FABRICATED-TEST particles OK");
		else TestMod.LOGGER.error("FABRICATED-TEST particles FAILED: {}", String.join("; ", failed));
	}
}
