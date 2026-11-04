package io.github.cmdralph.fabricated.forge.rendering;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Fabricated's seams for the start and the end of vanilla's HUD method ({@code Gui.render}, {@code m_280421_} in SRG's
 * names) on Forge 47 (HUD-4), added to {@code Gui} by its mixin. Forge's GUI ({@code ForgeGui}) overrides the method
 * and never calls it, so a Fabric mod's hook at its start or end applied to code that doesn't run. Pre-flight points
 * such hooks at these seams (core's {@code SkippedCode}), which take the method's parameters, and Forge's GUI calls them
 * where its own HUD drawing starts and ends (its mixin).
 */
public interface HudSeams {
	/**
	 * Calls the start seam where Forge's GUI starts drawing the HUD. Returns whether its hooks let the drawing go on: a
	 * Fabric mod's hook cancelling at the method's start skipped vanilla's drawing, and skips Forge's.
	 */
	boolean fabricated$startHud(GuiGraphics graphics, float partialTick);

	/** The start seam, where hooks at the method's start run: it records that none of them cancelled. */
	void fabricated$start$m_280421_(GuiGraphics graphics, float partialTick);

	/** The end seam, where hooks at the method's end run: called once Forge's GUI has drawn the HUD. */
	void fabricated$end$m_280421_(GuiGraphics graphics, float partialTick);
}
