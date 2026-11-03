package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;

/**
 * Adds a field to the server, counting its ticks, which {@link MinecraftServerCounterShadowMixin} shadows (CORPUS-74):
 * as Fabric API's renderer mixin adds the mesh MoreCulling's mixin shadows.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerCounterMixin {
	@Unique
	private int fabricatedForgeTest$ticks;

	@Inject(method = "method_3748", at = @At("HEAD"))
	private void fabricatedForgeTest$count(CallbackInfo ci) {
		fabricatedForgeTest$ticks++;
	}
}
