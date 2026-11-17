package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import io.github.cmdralph.fabricated.testmod.RecordedFramesTest;

/**
 * Takes the probe's {@code StringBuilder}, the only one, after the frame that chops a local (CORPUS-79, FRAME-1,
 * {@link RecordedFramesTest}). By its type and ordinal, not by its type alone: Mixin then applies the injector nowhere
 * where it finds none, as on Fabric and NeoForge, where by its type alone it would stop the probe's class from loading;
 * not required for that reason.
 */
@Mixin(RecordedFramesTest.Probe.class)
public abstract class RecordedFramesMixin {
	@ModifyVariable(method = "probe", at = @At(value = "INVOKE", target = "Ljava/lang/String;valueOf(Ljava/lang/Object;)Ljava/lang/String;"), ordinal = 0,
			require = 0, remap = false)
	private static StringBuilder fabricatedTest$kept(StringBuilder kept) {
		return kept.append("+local");
	}
}
