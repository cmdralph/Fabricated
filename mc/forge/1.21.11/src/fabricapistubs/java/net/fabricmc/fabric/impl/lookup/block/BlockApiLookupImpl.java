package net.fabricmc.fabric.impl.lookup.block;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public final class BlockApiLookupImpl<A, C> implements BlockApiLookup<A, C> {
	private BlockApiLookupImpl() {
	}

	@Override
	public @Nullable A find(Level world, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity blockEntity, C context) {
		throw new AssertionError("stand-in");
	}

	@Override
	public void registerForBlocks(BlockApiProvider<A, C> provider, net.minecraft.world.level.block.Block... blocks) {
		throw new AssertionError("stand-in");
	}

	public List<BlockApiProvider<A, C>> getFallbackProviders() {
		throw new AssertionError("stand-in");
	}
}
