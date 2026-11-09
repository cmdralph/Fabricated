package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import io.github.cmdralph.fabricated.forgetest.mixin.ParticleEngineProvidersAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.class_1059;
import net.minecraft.class_2400;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_707;
import net.minecraft.class_727;

/**
 * The client side of {@link ForgeParticleTest}: a factory for the test mod's particle type that takes a sprite
 * provider, registered from the client entrypoint as Fabric mods register theirs (on Forge 47 that runs before
 * {@code Minecraft}'s constructor makes the particle engine, which the check's line says). Once the game is up, the
 * particle engine must find the factory when a particle of the type is made ({@code ParticleEngine.createParticle},
 * {@code class_702.method_3056}), and the sprite provider must hold the one sprite the test mod's
 * {@code particles/sparkle.json} lists, in the particle atlas. Loading {@code TerrainParticle} ({@code class_727})
 * applies the module's block dust tint hook, which keeps it from loading if the hook doesn't apply. The engine's providers
 * by their type's raw id, as Porting Lib's accessor reads them where Forge 47 keeps them by the type's id, must hold the
 * factory's (CORPUS-37: the accessor invokes the in-game mod's bridge, a view of Forge's map).
 */
@Environment(EnvType.CLIENT)
public final class ForgeParticleClient {
	private static final class_2960 PARTICLE_ATLAS = new class_2960("minecraft", "textures/atlas/particles.png");
	private static volatile FabricSpriteProvider sprites;
	private static volatile class_707<class_2400> made;
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
			class_707<class_2400> factory = (options, level, x, y, z, xSpeed, ySpeed, zSpeed) -> {
				asked = true;
				return null;
			};
			made = factory;
			return factory;
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
		Int2ObjectMap<Object> byRawId = ((ParticleEngineProvidersAccessor) class_310.method_1551().field_1713).fabricatedtest$providers();
		int rawId = -1;
		for (Int2ObjectMap.Entry<Object> entry : byRawId.int2ObjectEntrySet()) {
			if (made != null && entry.getValue() == made) rawId = entry.getIntKey();
		}
		if (rawId < 0 || byRawId.get(rawId) != made || !byRawId.containsKey(rawId)) {
			failed.add("the providers by raw id (Porting Lib's accessor) " + (rawId < 0 ? "don't hold the factory's" : "don't give it for its raw id " + rawId));
		}
		// A tint hook that doesn't apply fails this load, which the caller logs.
		ForgeTestMod.LOGGER.debug("TerrainParticle is {} here", class_727.class.getName());
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST particles OK (factory registered {} the particle engine existed)",
					beforeEngine ? "before" : "after");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-TEST particles: {}", String.join("; ", failed));
		}
	}
}
