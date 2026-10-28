package io.github.cmdralph.fabricated.testmod;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/** The test fluids' liquid block, for {@link FluidTest} (its constructor is protected, so it takes a subclass). */
final class FluidTestCompat {
	private FluidTestCompat() {
	}

	static LiquidBlock liquidBlock(FlowingFluid fluid, BlockBehaviour.Properties properties) {
		return new TestLiquidBlock(fluid, properties);
	}

	private static final class TestLiquidBlock extends LiquidBlock {
		TestLiquidBlock(FlowingFluid fluid, BlockBehaviour.Properties properties) {
			super(fluid, properties);
		}

		// Compile-time only: javac sees Fabric's and NeoForge's getAppearance defaults on Block and needs a subclass
		// written against them to pick one (at runtime Block declares its own, so compiled Fabric mods don't need this).
		@Override
		public BlockState getAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side, @Nullable BlockState sourceState, @Nullable BlockPos sourcePos) {
			return state;
		}
	}
}
