package io.github.cmdralph.fabricated.testmod.mixinsemantics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.testmod.TestMod;
import net.minecraft.server.MinecraftServer;

/**
 * Adds a unique field of the same name as {@link MinecraftServerUniqueMixin}'s (CORPUS-95), and counts ten a tick in
 * it. Each mixin keeps a field of its own, as on Fabric, if it reads ten at the first tick's end; eleven, if the
 * platform's Mixin merged the two into one.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerUniqueTwinMixin {
	@Unique
	private int fabricatedTest$uniqueCount;

	@Unique
	private static boolean fabricatedTest$uniqueChecked;

	@Inject(method = "tickServer", at = @At("HEAD"))
	private void fabricatedTest$countTen(CallbackInfo ci) {
		fabricatedTest$uniqueCount += 10;
	}

	@Inject(method = "tickServer", at = @At("TAIL"))
	private void fabricatedTest$checkOwn(CallbackInfo ci) {
		if (fabricatedTest$uniqueChecked) return;
		fabricatedTest$uniqueChecked = true;
		if (fabricatedTest$uniqueCount == 10) {
			TestMod.LOGGER.info("FABRICATED-TEST unique mixin fields OK");
		} else {
			TestMod.LOGGER.warn("FABRICATED-TEST unique mixin fields shared: the twin reads {} after the first tick", fabricatedTest$uniqueCount);
		}
	}
}
