package io.github.cmdralph.fabricated.testmod.mixinsemantics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;

/**
 * Adds a unique field to the server of the same name as {@link MinecraftServerUniqueTwinMixin}'s (CORPUS-95), as Fabric
 * API's screen mixin and SpruceUI's both add {@code tickingScreen} to {@code Minecraft}, and counts one a tick in it.
 * Fabric's Mixin renames the later of the two fields; Forge 61 to 66's merged them into one.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerUniqueMixin {
	@Unique
	private int fabricatedTest$uniqueCount;

	@Inject(method = "tickServer", at = @At("HEAD"))
	private void fabricatedTest$countOne(CallbackInfo ci) {
		fabricatedTest$uniqueCount++;
	}
}
