package io.github.cmdralph.fabricated.forge.diagnostics.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.CrashReport;

import io.github.cmdralph.fabricated.diagnostics.CrashDiagnostics;
import io.github.cmdralph.fabricated.forge.diagnostics.LoadingErrors;

/**
 * A crash report's throwable may say a Fabric mod's mixin failed as Mixin applied it, as an injector's critical
 * failure ({@code InjectionError}) says only there, past Mixin's error handlers: that mixin is recorded for the next
 * launch to leave out (APPLY-1). A report Forge makes as mod loading fails has the failures as its errors' causes
 * instead, which it holds aside ({@link LoadingErrors}). Where the throwable involves a Fabric mod, the report's details
 * start with Fabricated's diagnostics block, which is logged too ({@link CrashDiagnostics}); the rest of the report is
 * left as it is. Forge 47 to 66.
 */
@Mixin(CrashReport.class)
abstract class CrashReportMixin {
	@Unique
	private String fabricated$diagnostics;

	@Inject(method = "<init>(Ljava/lang/String;Ljava/lang/Throwable;)V", at = @At("TAIL"))
	private void fabricated$diagnose(String title, Throwable exception, CallbackInfo ci) {
		fabricated$diagnostics = CrashDiagnostics.crashReport(exception, LoadingErrors.take());
	}

	@Inject(method = "getDetails(Ljava/lang/StringBuilder;)V", at = @At("HEAD"))
	private void fabricated$addDiagnostics(StringBuilder builder, CallbackInfo ci) {
		if (fabricated$diagnostics != null) builder.append(fabricated$diagnostics).append("\n\n");
	}
}
