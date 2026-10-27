package io.github.cmdralph.fabricated.forge.particle;

import net.fabricmc.fabric.api.client.particle.v1.ParticleRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric API's block dust tint event (fabric-particles-v1, passed through) where Forge 61 decides whether a block's
 * breaking particles are tinted (PART-2). Forge replaced the grass check in {@code TerrainParticle}'s constructor, which
 * upstream's hook looks for, with its block extensions' {@code areBreakingParticlesTinted}, so the plan leaves upstream's
 * hook out and {@code TerrainParticleTintMixin} asks the event there instead, as Forge 47's module does.
 */
public final class BlockDustTint {
	/** Fabric API's particles module is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-particles-v1");

	private BlockDustTint() {
	}

	/** Whether Fabric mods let the particle be tinted: always without Fabric API's particles module. */
	public static boolean allowed(BlockState state, ClientLevel level, BlockPos pos) {
		return !ENABLED || Upstream.allowed(state, level, pos);
	}

	/** Everything that names Fabric's particles module: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Upstream {
		private Upstream() {
		}

		static boolean allowed(BlockState state, ClientLevel level, BlockPos pos) {
			return ParticleRenderEvents.ALLOW_BLOCK_DUST_TINT.invoker().allowBlockDustTint(state, level, pos);
		}
	}
}
