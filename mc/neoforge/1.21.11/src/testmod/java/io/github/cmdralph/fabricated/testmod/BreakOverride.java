package io.github.cmdralph.fabricated.testmod;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * A block of the platform's kind for the vanilla block breaking check ({@link VanillaBreakTest}, SEAM-3): it overrides
 * NeoForge's {@code onDestroyedByPlayer}, leaving amethyst where a player breaks it.
 */
final class BreakOverride extends Block {
	BreakOverride(Properties properties) {
		super(properties);
	}

	@Override
	public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack, boolean willHarvest, FluidState fluid) {
		return level.setBlock(pos, Blocks.AMETHYST_BLOCK.defaultBlockState(), 3);
	}

	// Compile-time only: javac sees Fabric's and NeoForge's getAppearance defaults on Block and needs a subclass written
	// against them to pick one (at runtime Block declares its own, so compiled Fabric mods don't need this).
	@Override
	public BlockState getAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side, @Nullable BlockState sourceState, @Nullable BlockPos sourcePos) {
		return state;
	}
}
