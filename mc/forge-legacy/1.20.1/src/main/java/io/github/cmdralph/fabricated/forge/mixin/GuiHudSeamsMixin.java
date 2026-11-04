package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import io.github.cmdralph.fabricated.forge.rendering.HudSeams;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Adds {@link HudSeams} to {@code Gui}: the seams for the start and the end of its HUD method, which Fabric mods' hooks
 * there are pointed at (HUD-4), named after the method's SRG name, as pre-flight reads the game. They are public, and
 * not final, so Mixin applies Fabric mods' injectors to them (it refuses a merged {@code @Final} method).
 */
@Mixin(Gui.class)
abstract class GuiHudSeamsMixin implements HudSeams {
	/** Set by the start seam's own code, which runs only when no hook at its start cancelled. */
	@Unique
	private boolean fabricated$hudGoesOn;

	@Override
	public boolean fabricated$startHud(GuiGraphics graphics, float partialTick) {
		fabricated$hudGoesOn = false;
		fabricated$start$m_280421_(graphics, partialTick);
		return fabricated$hudGoesOn;
	}

	@Override
	public void fabricated$start$m_280421_(GuiGraphics graphics, float partialTick) {
		fabricated$hudGoesOn = true;
	}

	@Override
	public void fabricated$end$m_280421_(GuiGraphics graphics, float partialTick) {
	}
}
