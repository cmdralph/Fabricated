package io.github.cmdralph.fabricated.testmod.mixinsemantics;

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
	private int fabricatedTest$ticks;

	@Inject(method = "tickServer", at = @At("HEAD"))
	private void fabricatedTest$count(CallbackInfo ci) {
		fabricatedTest$ticks++;
	}
}
