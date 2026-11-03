package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forgetest.ForgeTestMod;
import net.minecraft.server.MinecraftServer;

/**
 * Shadows the field {@link MinecraftServerCounterMixin} adds (CORPUS-74). Fabric's Mixin finds such a shadow among the
 * fields of the mixins it prepared before; Forge's looks in the class's own only and stopped the game on MoreCulling's,
 * so Fabricated declares the shadow as the other mixin's field, one field once Mixin merged both. Applied after the
 * other (a higher priority, as MoreCulling's is), it reads the count the other's code keeps: one at the first tick's end.
 */
@Mixin(value = MinecraftServer.class, priority = 1200)
public abstract class MinecraftServerCounterShadowMixin {
	@Shadow
	private int fabricatedForgeTest$ticks;

	@Unique
	private static boolean fabricatedForgeTest$checked;

	@Inject(method = "method_3748", at = @At("TAIL"))
	private void fabricatedForgeTest$checkShared(CallbackInfo ci) {
		if (fabricatedForgeTest$checked) return;
		fabricatedForgeTest$checked = true;
		if (fabricatedForgeTest$ticks == 1) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST shared mixin field OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST shared mixin field not shared: the shadow reads {} after the first tick", fabricatedForgeTest$ticks);
		}
	}
}
