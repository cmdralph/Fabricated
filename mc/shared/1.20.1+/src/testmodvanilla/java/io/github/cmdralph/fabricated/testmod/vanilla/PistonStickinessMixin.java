package io.github.cmdralph.fabricated.testmod.vanilla;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.testmod.VanillaPistonTest;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hooks of vanilla's piston stickiness methods as Fabric mods write them, which the platforms replaced with the block
 * state's extensions (SEAM-1, {@link VanillaPistonTest}): Carpet's {@code @Inject} at the start of {@code isSticky}, and
 * FrozenLib's and Moonlight's {@code @WrapOperation}s of the resolver's calls of {@code isSticky} and
 * {@code canStickToEachOther}. A {@code @WrapOperation} composes with other mods' hooks of the same call (Carpet's
 * {@code @Redirect} among them), as the advanced corpus loads them together.
 */
@Mixin(PistonStructureResolver.class)
public abstract class PistonStickinessMixin {
	@Inject(method = "isSticky", at = @At("HEAD"), cancellable = true)
	private static void fabricatedTest$sticky(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (VanillaPistonTest.sticky(state)) cir.setReturnValue(true);
	}

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
