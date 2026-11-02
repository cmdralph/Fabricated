package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.Util;

import io.github.cmdralph.fabricated.neoforge.MixinFailureRecorder;

/**
 * A throwable the game's worker threads stop on may say a Fabric mod's mixin failed as Mixin applied it: the mixin is
 * recorded for the next launch to leave out (APPLY-2, as {@code CrashReportMixin} does for the game's crash reports). The
 * workers' pool hands it to {@code Util.onThreadException}, past the default uncaught exception handler
 * {@link MixinFailureRecorder} installs, and the game logs it and goes on: a redirect into a structure's or a spawner's
 * code, failing as world generation loaded the class, left the 1.21.1 advanced corpus server waiting for its spawn chunks
 * and never recorded. What the game does with the throwable is left as it is. A game build without the method keeps
 * only this record from working ({@code require = 0}).
 */
@Mixin(Util.class)
abstract class UtilMixin {
	@Inject(method = "onThreadException(Ljava/lang/Thread;Ljava/lang/Throwable;)V", at = @At("HEAD"), require = 0)
	private static void fabricated$recordMixinFailure(Thread thread, Throwable thrown, CallbackInfo ci) {
		MixinFailureRecorder.record(thrown);
	}
}
