package io.github.cmdralph.fabricated.forge.diagnostics.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.server.Main;

import io.github.cmdralph.fabricated.diagnostics.CrashDiagnostics;

/**
 * The dedicated server's {@code main} logs what stops it from starting ("Failed to start the minecraft server", its one
 * call of {@code Logger.error(Marker, String, Throwable)} on Forge 47 to 66) and returns: no crash report is made and
 * no thread dies of it. Forge 61 to 66 catch any throwable there, an injector's critical failure as the server's class
 * loads among them, which past Mixin's error handlers only this sees: the mixin it names is recorded for the next
 * launch to leave out (APPLY-3), and the diagnostics block names the Fabric mods it involves ({@link CrashDiagnostics}).
 * The throwable is logged as it was.
 */
@Mixin(Main.class)
abstract class ServerMainMixin {
	@ModifyArg(method = "main", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Lorg/slf4j/Marker;Ljava/lang/String;Ljava/lang/Throwable;)V",
			remap = false), index = 2)
	private static Throwable fabricated$diagnose(Throwable thrown) {
		CrashDiagnostics.stopped(thrown);
		return thrown;
	}
}
