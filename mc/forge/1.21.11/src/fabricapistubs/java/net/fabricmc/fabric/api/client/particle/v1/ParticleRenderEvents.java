package net.fabricmc.fabric.api.client.particle.v1;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.event.Event;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod and the forge test mod
 * call, in Mojang's names.
 */
public final class ParticleRenderEvents {
	public static final Event<AllowBlockDustTint> ALLOW_BLOCK_DUST_TINT = null;

	private ParticleRenderEvents() {
	}

	@FunctionalInterface
	public interface AllowBlockDustTint {
		boolean allowBlockDustTint(BlockState state, ClientLevel world, BlockPos pos);
	}
}
