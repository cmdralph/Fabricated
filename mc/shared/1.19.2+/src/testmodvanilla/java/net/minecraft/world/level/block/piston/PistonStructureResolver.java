package net.minecraft.world.level.block.piston;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stand-in, never packaged: vanilla's piston structure resolver as the test mod's check and mixin name it, with the
 * stickiness methods the platforms took out (SEAM-1), which Forge 47's annotation processor maps for the mixin's refmap.
 */
public class PistonStructureResolver {
	public PistonStructureResolver(Level level, BlockPos pistonPos, Direction pistonDirection, boolean extending) {
	}

	public boolean resolve() {
		return false;
	}

	private static boolean isSticky(BlockState state) {
		return false;
	}

	private static boolean canStickToEachOther(BlockState state1, BlockState state2) {
		return false;
	}

	private boolean addBlockLine(BlockPos start, Direction direction) {
		return false;
	}

	private boolean addBranchingBlocks(BlockPos fromPos) {
		return false;
	}

	public List<BlockPos> getToPush() {
		return List.of();
	}
}
