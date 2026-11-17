package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.forge.content.FabricFlammableBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.extensions.IForgeBlock;

/**
 * Forge 43's flammability check, {@code IForgeBlock.isFlammable}, for every block that doesn't answer it itself: a block
 * Fabric API's registry makes flammable is by its odds alone, whatever its material ({@link FabricFlammableBlocks}).
 * The rest are as Forge's default has them. Forge's own method, by its own name, not remapped.
 */
@Mixin(Block.class)
abstract class BlockFabricFlammableMixin implements IForgeBlock {
	@Override
	public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return (state.getMaterial().isFlammable() || FabricFlammableBlocks.registered(state.getBlock())) && state.getFlammability(level, pos, direction) > 0;
	}
}
