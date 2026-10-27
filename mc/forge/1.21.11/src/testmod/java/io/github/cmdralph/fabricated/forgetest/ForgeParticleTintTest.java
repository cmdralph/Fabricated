package io.github.cmdralph.fabricated.forgetest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleRenderEvents;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * The forge test mod's part for fabric-particles-v1's block dust tint event on Forge 61, which Fabricated's in-game mod
 * asks where Forge decides a breaking particle's tint, upstream's hook looking for the check Forge replaced (PART-2;
 * with Fabric API's jar only): a stone particle made as the client joins must reach the test mod's callback.
 */
@Environment(EnvType.CLIENT)
public final class ForgeParticleTintTest {
	private static volatile boolean asked;

	private ForgeParticleTintTest() {
	}

	/** From the client entrypoint, with Fabric API's particles and networking modules. */
	public static void register() {
		ParticleRenderEvents.ALLOW_BLOCK_DUST_TINT.register((state, world, pos) -> {
			if (state.is(Blocks.STONE)) asked = true;
			return true;
		});
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
			new TerrainParticle(client.level, 0.5, 64.0, 0.5, 0.0, 0.0, 0.0, Blocks.STONE.defaultBlockState(), BlockPos.ZERO);
			if (asked) {
				ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST block dust tint event OK");
			} else {
				ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST block dust tint event: a stone particle didn't ask it");
			}
		});
	}
}
