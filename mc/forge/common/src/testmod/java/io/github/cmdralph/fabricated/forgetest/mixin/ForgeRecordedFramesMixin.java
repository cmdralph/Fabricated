package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import io.github.cmdralph.fabricated.forgetest.ForgeRecordedFramesTest;

/**
 * Takes the probe's {@code StringBuilder} by its type alone, after the frame that chops a local, where Forge's Mixin
 * would find none without Fabricated's repair of the frames it recorded (CORPUS-79, {@link ForgeRecordedFramesTest}).
 */
@Mixin(ForgeRecordedFramesTest.Probe.class)
public abstract class ForgeRecordedFramesMixin {
	@ModifyVariable(method = "probe", at = @At(value = "INVOKE", target = "Ljava/lang/String;valueOf(Ljava/lang/Object;)Ljava/lang/String;"), remap = false)
	private static StringBuilder fabricatedForgeTest$kept(StringBuilder kept) {
		return kept.append("+local");
	}
}
