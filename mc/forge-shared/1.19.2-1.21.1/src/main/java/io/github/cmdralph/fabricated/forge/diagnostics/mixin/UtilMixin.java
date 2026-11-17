package io.github.cmdralph.fabricated.forge.diagnostics.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.Util;

import io.github.cmdralph.fabricated.diagnostics.CrashDiagnostics;

/**
 * A throwable the game's worker threads stop on may say a Fabric mod's mixin failed as Mixin applied it: the mixin is
 * recorded for the next launch to leave out (APPLY-2, as {@code CrashReportMixin} does for the game's crash reports),
 * and the diagnostics block names the Fabric mods it involves in the log ({@link CrashDiagnostics}). The workers' pool
 * hands it to {@code Util.onThreadException}, past the default uncaught exception handler {@link CrashDiagnostics}
 * installs, and the game logs it and goes on. What the game does with the throwable is left as it is. A game build
 * without the method keeps only this hook from working ({@code require = 0}). Forge 43, 47 and 52's, whose {@code Util}
 * is {@code net.minecraft.Util}.
 */
@Mixin(Util.class)
abstract class UtilMixin {
	@Inject(method = "onThreadException(Ljava/lang/Thread;Ljava/lang/Throwable;)V", at = @At("HEAD"), require = 0)
	private static void fabricated$diagnose(Thread thread, Throwable thrown, CallbackInfo ci) {
		CrashDiagnostics.stopped(thrown);
	}
}
