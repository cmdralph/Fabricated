package net.fabricmc.fabric.api.lookup.v1.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod and the forge test mod
 * call, in Mojang's names.
 */
public interface BlockApiLookup<A, C> {
	@Nullable
	A find(Level world, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity blockEntity, C context);

	@Nullable
	default A find(Level world, BlockPos pos, C context) {
		throw new AssertionError("stand-in");
	}

	void registerForBlocks(BlockApiProvider<A, C> provider, Block... blocks);

	@FunctionalInterface
	interface BlockApiProvider<A, C> {
		@Nullable
		A find(Level world, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, C context);
	}
}
