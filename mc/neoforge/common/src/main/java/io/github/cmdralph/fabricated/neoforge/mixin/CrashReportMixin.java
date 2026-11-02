package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.CrashReport;

import io.github.cmdralph.fabricated.neoforge.MixinFailureRecorder;

/**
 * A crash report's throwable may say a Fabric mod's mixin failed as Mixin applied it, as an injector's critical
 * failure ({@code InjectionError}) says only there, past Mixin's error handlers: that mixin is recorded for the next
 * launch to leave out (APPLY-1). The report itself is left as it is.
 */
@Mixin(CrashReport.class)
abstract class CrashReportMixin {
	@Inject(method = "<init>(Ljava/lang/String;Ljava/lang/Throwable;)V", at = @At("TAIL"))
	private void fabricated$recordMixinFailure(String title, Throwable exception, CallbackInfo ci) {
		MixinFailureRecorder.record(exception);
	}
}
