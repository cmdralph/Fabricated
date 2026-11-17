package io.github.cmdralph.fabricated.forge.diagnostics.mixin;

import java.io.File;

import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraftforge.fml.LoadingFailedException;
import net.minecraftforge.logging.CrashReportExtender;

import io.github.cmdralph.fabricated.forge.diagnostics.LoadingErrors;

/**
 * Forge's crash report for a mod loading that failed holds a placeholder exception, its errors only as text: they are
 * held for the report it makes next ({@link LoadingErrors}), which reads the Fabric mods' mixin failures and the Fabric
 * mods involved off their causes ({@code CrashReportMixin}). Forge 47 to 66, on the server and the client; the dump is
 * left as it is.
 */
@Mixin(value = CrashReportExtender.class, remap = false)
abstract class CrashReportExtenderMixin {
	@Inject(method = "dumpModLoadingCrashReport(Lorg/apache/logging/log4j/Logger;Lnet/minecraftforge/fml/LoadingFailedException;Ljava/io/File;)Ljava/io/File;",
			at = @At("HEAD"))
	private static void fabricated$holdLoadingErrors(Logger logger, LoadingFailedException error, File topLevelDir, CallbackInfoReturnable<File> cir) {
		LoadingErrors.hold(error);
	}
}
