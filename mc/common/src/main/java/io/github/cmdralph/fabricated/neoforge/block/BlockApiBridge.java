package io.github.cmdralph.fabricated.neoforge.block;

import net.fabricmc.fabric.api.block.v1.BlockFunctionalityTags;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Native {@code fabric-block-api-v1}, shared part: what Fabricated's block mixins answer NeoForge's block hooks with. */
public final class BlockApiBridge {
	private BlockApiBridge() {
	}

	/**
	 * NeoForge's {@code makesOpenTrapdoorAboveClimbable}: a ladder facing the trapdoor's way (NeoForge's and vanilla's
	 * rule), or any block in Fabric's {@code fabric:can_climb_trapdoor_above} tag, as upstream.
	 */
	public static boolean makesOpenTrapdoorAboveClimbable(BlockState state, BlockState trapdoorState) {
		if (state.getBlock() instanceof LadderBlock) {
			return state.getValue(LadderBlock.FACING) == trapdoorState.getValue(TrapDoorBlock.FACING);
		}
		return state.is(BlockFunctionalityTags.CAN_CLIMB_TRAPDOOR_ABOVE);
	}
}
