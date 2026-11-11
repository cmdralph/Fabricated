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
 * Handlers whose shape pre-flight's bridges adapt, each logging once as the server ticks: one taking a supertype of the
 * parameter the method takes, as JEI's and owo-lib's char typed hooks take the {@code GuiEventListener} the platform's
 * lambda passes as a {@code Screen} (LAMBDA-11), and a static one on a method that takes {@code this}, which Fabric's
 * Mixin takes and Forge 47's refuses (LAMBDA-12).
 */
@Mixin(MinecraftServer.class)
public abstract class PreflightShapesMixin {
	@Unique
	private static boolean fabricatedTest$supertypeLogged;
	@Unique
	private static boolean fabricatedTest$staticLogged;

	@Inject(method = "tickServer", at = @At("HEAD"))
	private void fabricatedTest$supertype(Object hasTimeLeft, CallbackInfo ci) {
		if (fabricatedTest$supertypeLogged || !(hasTimeLeft instanceof BooleanSupplier)) return;
		fabricatedTest$supertypeLogged = true;
		TestMod.LOGGER.info("FABRICATED-TEST supertype handler OK");
	}

	@Inject(method = "tickServer", at = @At("HEAD"))
	private static void fabricatedTest$static(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
		if (fabricatedTest$staticLogged || hasTimeLeft == null) return;
		fabricatedTest$staticLogged = true;
		TestMod.LOGGER.info("FABRICATED-TEST static handler OK");
	}
}
