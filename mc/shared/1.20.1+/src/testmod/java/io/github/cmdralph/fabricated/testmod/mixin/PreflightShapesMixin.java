package io.github.cmdralph.fabricated.testmod.mixin;

import java.util.function.BooleanSupplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.testmod.TestMod;
import net.minecraft.server.MinecraftServer;

/**
 * A handler whose shape pre-flight's bridge adapts, logging once as the server ticks: a static one on a method that takes
 * {@code this}, which Fabric's Mixin takes and Forge 47's refuses (LAMBDA-12).
 */
@Mixin(MinecraftServer.class)
public abstract class PreflightShapesMixin {
	@Unique
	private static boolean fabricatedTest$staticLogged;

	@Inject(method = "tickServer", at = @At("HEAD"))
	private static void fabricatedTest$static(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
		if (fabricatedTest$staticLogged || hasTimeLeft == null) return;
		fabricatedTest$staticLogged = true;
		TestMod.LOGGER.info("FABRICATED-TEST static handler OK");
	}
}
