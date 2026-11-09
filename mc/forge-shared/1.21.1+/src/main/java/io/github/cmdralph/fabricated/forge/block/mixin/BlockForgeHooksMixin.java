package io.github.cmdralph.fabricated.forge.block.mixin;

import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.forge.block.BlockHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Answers Forge's trapdoor ladder hook with Fabric's {@code fabric:can_climb_trapdoor_above} tag too
 * ({@link BlockHooks}). Only adds a method to {@code Block}; a block overriding it keeps its own.
 */
@Mixin(Block.class)
abstract class BlockForgeHooksMixin {
	public boolean makesOpenTrapdoorAboveClimbable(BlockState state, LevelReader level, BlockPos pos, BlockState trapdoorState) {
		return BlockHooks.makesOpenTrapdoorAboveClimbable(state, trapdoorState);
	}
}
