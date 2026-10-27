package net.fabricmc.fabric.api.lookup.v1.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compile-time stand-in: the members of Fabric API's 1.20.1 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public interface BlockApiLookup<A, C> {
	@Nullable
	A find(Level world, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity blockEntity, C context);

	@FunctionalInterface
	interface BlockApiProvider<A, C> {
		@Nullable
		A find(Level world, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, C context);
	}
}
