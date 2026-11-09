package io.github.cmdralph.fabricated.testmod.mixinsemantics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.testmod.TestMod;
import net.minecraft.server.MinecraftServer;

/**
 * Shadows the field {@link MinecraftServerCounterMixin} adds (CORPUS-74). Fabric's Mixin finds such a shadow among the
 * fields of the mixins it prepared before; Forge 61 to 66's looks in the class's own only and stopped the game on
 * MoreCulling's. Applied after the other (a higher priority, as MoreCulling's is), it reads the count the other's code
 * keeps: one at the first tick's end.
 */
@Mixin(value = MinecraftServer.class, priority = 1200)
public abstract class MinecraftServerCounterShadowMixin {
	@Shadow
	private int fabricatedTest$ticks;

	@Unique
	private static boolean fabricatedTest$checked;

	@Inject(method = "tickServer", at = @At("TAIL"))
	private void fabricatedTest$checkShared(CallbackInfo ci) {
		if (fabricatedTest$checked) return;
		fabricatedTest$checked = true;
		if (fabricatedTest$ticks == 1) {
			TestMod.LOGGER.info("FABRICATED-TEST shared mixin field OK");
		} else {
			TestMod.LOGGER.warn("FABRICATED-TEST shared mixin field not shared: the shadow reads {} after the first tick", fabricatedTest$ticks);
		}
	}
}
