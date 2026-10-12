package io.github.cmdralph.fabricated.neoforge.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import io.github.cmdralph.fabricated.neoforge.block.BlockApiBridge;
import net.fabricmc.fabric.api.block.v1.FabricBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Makes every block Fabric's {@link FabricBlock} and answers NeoForge's block hooks from it (1.21.11). Only adds methods;
 * no Minecraft code is changed. Blocks that override these hooks themselves (NeoForge mods' blocks) keep their own.
 */
@Mixin(Block.class)
abstract class BlockMixin implements FabricBlock {
	/**
	 * Fabric's and NeoForge's {@code getAppearance} have the same signature, so Block needs its own: two inherited
	 * defaults would make every call fail. Both defaults return the state itself, and a block overriding it overrides
	 * both.
	 */
	@Override
	public BlockState getAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side, @Nullable BlockState sourceState, @Nullable BlockPos sourcePos) {
		return state;
	}

	/** NeoForge's trapdoor-ladder hook, extended with Fabric's {@code fabric:can_climb_trapdoor_above} tag. */
	public boolean makesOpenTrapdoorAboveClimbable(BlockState state, LevelReader level, BlockPos pos, BlockState trapdoorState) {
		return BlockApiBridge.makesOpenTrapdoorAboveClimbable(state, trapdoorState);
	}
}
