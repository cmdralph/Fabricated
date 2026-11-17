package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.server.Main;

import io.github.cmdralph.fabricated.diagnostics.CrashDiagnostics;

/**
 * The dedicated server's {@code main} logs what stops it from starting ("Failed to start the minecraft server", its one
 * call of {@code Logger.error(Marker, String, Throwable)} on NeoForge 21.1 to 26.3) and returns: no crash report is
 * made and no thread dies of it. NeoForge 21.11 to 26.3 catch any throwable there, an injector's critical failure as a
 * class first loads there among them (the server's own, after mod loading), which past Mixin's error handlers only this
 * sees: the mixin it names is recorded for the next launch to leave out (APPLY-4), and the diagnostics block names the
 * Fabric mods it involves ({@link CrashDiagnostics}). The throwable is logged as it was.
 */
@Mixin(Main.class)
abstract class ServerMainMixin {
	@ModifyArg(method = "main", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Lorg/slf4j/Marker;Ljava/lang/String;Ljava/lang/Throwable;)V"),
			index = 2)
	private static Throwable fabricated$diagnose(Throwable thrown) {
		CrashDiagnostics.stopped(thrown);
		return thrown;
	}
}
