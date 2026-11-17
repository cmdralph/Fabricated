package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.cmdralph.fabricated.forge.rendering.FabricHudCallback;
import io.github.cmdralph.fabricated.forge.rendering.HudSeams;

/**
 * Calls the HUD method's seams ({@link HudSeams}) where Forge 43's GUI draws the HUD in that method's place (HUD-4): the
 * start seam first, before Forge's {@code RenderGuiEvent.Pre}, the drawing skipped when a hook there cancelled, and the
 * end seam last, after {@code RenderGuiEvent.Post}, just after Fabric's {@code HudRenderCallback}
 * ({@link FabricHudCallback}). A Forge mod that cancels {@code Pre} hides the whole HUD, so neither runs then.
 *
 * <p>Forge's jar is in SRG's names at run time, so the target is its override of {@code Gui.render}, {@code m_93030_},
 * and nothing in this mixin is remapped.
 */
@Mixin(targets = "net.minecraftforge.client.gui.overlay.ForgeGui", remap = false)
abstract class ForgeGuiHudSeamsMixin {
	@Inject(method = "m_93030_", at = @At("HEAD"), cancellable = true)
	private void fabricated$atHudStart(PoseStack poseStack, float partialTick, CallbackInfo ci) {
		if (!((HudSeams) (Object) this).fabricated$startHud(poseStack, partialTick)) ci.cancel();
	}

	@Inject(method = "m_93030_", at = @At("TAIL"))
	private void fabricated$atHudEnd(PoseStack poseStack, float partialTick, CallbackInfo ci) {
		FabricHudCallback.fire(poseStack, partialTick);
		((HudSeams) (Object) this).fabricated$end$m_93030_(poseStack, partialTick);
	}
}
