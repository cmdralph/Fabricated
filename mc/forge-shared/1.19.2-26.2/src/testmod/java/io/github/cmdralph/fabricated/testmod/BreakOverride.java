package io.github.cmdralph.fabricated.testmod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

/**
 * A block of the platform's kind for the vanilla block breaking and harvest checks ({@link VanillaBreakTest}, SEAM-3;
 * {@link VanillaHarvestTest}, SEAM-4): it overrides Forge's {@code onDestroyedByPlayer}, leaving amethyst where a
 * player breaks it, {@code canHarvestBlock}, answering its harvest check itself, {@code getFriction}, answering its
 * friction itself ({@link VanillaFrictionTest}, SEAM-8), and {@code getToolModifiedState}, making the shovel's path amethyst
 * ({@link VanillaShovelTest}, SEAM-13).
 */
final class BreakOverride extends Block {
	BreakOverride(Properties properties) {
		super(properties);
	}

	@Override
	public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
		return level.setBlock(pos, Blocks.AMETHYST_BLOCK.defaultBlockState(), 3);
	}

	// The platform's harvest check, answered by the block itself (SEAM-4): vanilla's call isn't put back for it.
	@Override
	public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
		return VanillaHarvestTest.overridingAnswer();
	}

	// The platform's friction, answered by the block itself (SEAM-8): vanilla's call isn't put back for it.
	@Override
	public float getFriction(BlockState state, LevelReader level, BlockPos pos, Entity entity) {
		return VanillaFrictionTest.overridingFriction();
	}

	// The platform's tool modifications, answered by the block itself (SEAM-13): vanilla's path lookup isn't put back for it.
	@Override
	public BlockState getToolModifiedState(BlockState state, UseOnContext context, ToolAction action, boolean simulate) {
		return action == ToolActions.SHOVEL_FLATTEN ? Blocks.AMETHYST_BLOCK.defaultBlockState() : null;
	}
}
