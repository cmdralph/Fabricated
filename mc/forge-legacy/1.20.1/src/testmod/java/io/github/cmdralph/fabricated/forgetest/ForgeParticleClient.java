package io.github.cmdralph.fabricated.forgetest;

import io.github.cmdralph.fabricated.forgetest.mixin.ParticleEngineProvidersAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The particle engine's providers by their type's raw id, as Porting Lib's accessor reads them where Forge 47 keeps them
 * by the type's id: the accessor invokes the in-game mod's bridge, a view of Forge's map, which must hold the flame's
 * provider under the flame's raw id (CORPUS-37). The shared suite's particle check is the test mod's
 * {@code ParticleClientTest}.
 */
@Environment(EnvType.CLIENT)
public final class ForgeParticleClient {
	private ForgeParticleClient() {
	}

	/** After the first tick of the client's integrated server, when the particle engine has its providers. */
	public static void check() {
		Int2ObjectMap<Object> byRawId = ((ParticleEngineProvidersAccessor) Minecraft.getInstance().particleEngine).fabricatedtest$providers();
		int flame = BuiltInRegistries.PARTICLE_TYPE.getId(ParticleTypes.FLAME);
		if (byRawId.containsKey(flame) && byRawId.get(flame) != null) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST particle providers by raw id OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST particle providers by raw id FAILED: the accessor's view has nothing for the flame's raw id {}", flame);
		}
	}
}
