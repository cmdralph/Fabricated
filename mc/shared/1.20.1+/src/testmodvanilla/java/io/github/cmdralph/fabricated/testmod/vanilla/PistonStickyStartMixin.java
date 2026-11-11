package io.github.cmdralph.fabricated.testmod.vanilla;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.testmod.VanillaPistonTest;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Carpet's hook of vanilla's piston stickiness methods (SEAM-1, {@link VanillaPistonTest}): an {@code @Inject} at the
 * start of {@code isSticky}, making smooth basalt sticky. Mixin applies a mixin of a lower priority first, and of the
 * callbacks it puts at one method's start the first applied runs first: so this one answers for smooth basalt before the
 * hooks of the default priority, Carpet 1.20.1's among them, which answers for every block.
 */
@Mixin(value = PistonStructureResolver.class, priority = 900)
public abstract class PistonStickyStartMixin {
	@Inject(method = "isSticky", at = @At("HEAD"), cancellable = true)
	private static void fabricatedTest$sticky(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (VanillaPistonTest.sticky(state)) cir.setReturnValue(true);
	}
}
