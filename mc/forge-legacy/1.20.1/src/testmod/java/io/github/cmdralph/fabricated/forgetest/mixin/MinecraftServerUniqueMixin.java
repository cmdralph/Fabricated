package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;

/**
 * Adds a unique field to the server of the same name as {@link MinecraftServerUniqueTwinMixin}'s (CORPUS-95), as Fabric
 * API's screen mixin and SpruceUI's both add {@code tickingScreen} to {@code Minecraft}, and counts one a tick in it.
 * Fabric's Mixin renames the later of the two fields; Forge's merged them into one, so Fabricated renames them.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerUniqueMixin {
	@Unique
	private int fabricatedForgeTest$uniqueCount;

	@Inject(method = "method_3748", at = @At("HEAD"))
	private void fabricatedForgeTest$countOne(CallbackInfo ci) {
		fabricatedForgeTest$uniqueCount++;
	}
}
