package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forgetest.ForgeTestMod;
import net.minecraft.server.MinecraftServer;

/**
 * Adds a unique field of the same name as {@link MinecraftServerUniqueMixin}'s (CORPUS-95), and counts ten a tick in
 * it. Each mixin keeps a field of its own, as on Fabric, if it reads ten at the first tick's end; eleven, if Forge's
 * Mixin merged the two into one.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerUniqueTwinMixin {
	@Unique
	private int fabricatedForgeTest$uniqueCount;

	@Unique
	private static boolean fabricatedForgeTest$uniqueChecked;

	@Inject(method = "tickServer", at = @At("HEAD"))
	private void fabricatedForgeTest$countTen(CallbackInfo ci) {
		fabricatedForgeTest$uniqueCount += 10;
	}

	@Inject(method = "tickServer", at = @At("TAIL"))
	private void fabricatedForgeTest$checkOwn(CallbackInfo ci) {
		if (fabricatedForgeTest$uniqueChecked) return;
		fabricatedForgeTest$uniqueChecked = true;
		if (fabricatedForgeTest$uniqueCount == 10) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST unique mixin fields OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST unique mixin fields shared: the twin reads {} after the first tick", fabricatedForgeTest$uniqueCount);
		}
	}
}
