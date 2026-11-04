package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forge.rendering.HudSeams;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Calls the HUD method's seams ({@link HudSeams}) where Forge 47's GUI draws the HUD in that method's place (HUD-4): the
 * start seam first, before Forge's {@code RenderGuiEvent.Pre}, the drawing skipped when a hook there cancelled, and the
 * end seam last, after {@code RenderGuiEvent.Post}. A Forge mod that cancels {@code Pre} hides the whole HUD, so the end
 * seam isn't called then, as Fabric's {@code HudRenderCallback} wasn't before.
 *
 * <p>Forge's jar is in SRG's names at run time, so the target is its override of {@code Gui.render}, {@code m_280421_},
 * and nothing in this mixin is remapped.
 */
@Mixin(targets = "net.minecraftforge.client.gui.overlay.ForgeGui", remap = false)
abstract class ForgeGuiHudSeamsMixin {
	@Inject(method = "m_280421_", at = @At("HEAD"), cancellable = true)
	private void fabricated$atHudStart(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
		if (!((HudSeams) (Object) this).fabricated$startHud(graphics, partialTick)) ci.cancel();
	}

	@Inject(method = "m_280421_", at = @At("TAIL"))
	private void fabricated$atHudEnd(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
		((HudSeams) (Object) this).fabricated$end$m_280421_(graphics, partialTick);
	}
}
