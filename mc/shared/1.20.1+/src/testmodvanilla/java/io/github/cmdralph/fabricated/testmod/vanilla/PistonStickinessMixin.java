package io.github.cmdralph.fabricated.testmod.vanilla;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.testmod.VanillaPistonTest;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;

/**
 * FrozenLib's and Moonlight's hooks of vanilla's piston stickiness methods, which the platforms replaced with the block
 * state's extensions (SEAM-1, {@link VanillaPistonTest}): {@code @WrapOperation}s of the resolver's calls of
 * {@code isSticky} and {@code canStickToEachOther}. Applied after other mods' hooks of the default priority, they wrap
 * those (Carpet's {@code @Redirect}s of the same calls among them), as the advanced corpus loads them together.
 */
@Mixin(PistonStructureResolver.class)
public abstract class PistonStickinessMixin {
	@WrapOperation(method = {"resolve", "addBlockLine"}, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;isSticky(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean fabricatedTest$askedSticky(BlockState state, Operation<Boolean> original) {
		VanillaPistonTest.askedSticky();
		return original.call(state);
	}

	@WrapOperation(method = {"addBlockLine", "addBranchingBlocks"}, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;canStickToEachOther(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean fabricatedTest$stick(BlockState state, BlockState other, Operation<Boolean> original) {
		Boolean stick = VanillaPistonTest.stick(state, other);
		return stick != null ? stick : original.call(state, other);
	}
}
