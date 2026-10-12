package io.github.cmdralph.fabricated.neoforge.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import net.fabricmc.fabric.api.block.v1.FabricBlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;

/**
 * Makes every block state Fabric's {@link FabricBlockState} (1.21.11). Its {@code getAppearance} has the same signature
 * as NeoForge's, so the state needs its own; it asks the block, as both defaults do. Only adds methods.
 */
@Mixin(BlockState.class)
abstract class BlockStateMixin implements FabricBlockState {
	@Override
	public BlockState getAppearance(BlockAndTintGetter level, BlockPos pos, Direction side, @Nullable BlockState sourceState, @Nullable BlockPos sourcePos) {
		BlockState self = (BlockState) (Object) this;
		return ((IBlockExtension) self.getBlock()).getAppearance(self, level, pos, side, sourceState, sourcePos);
	}
}
