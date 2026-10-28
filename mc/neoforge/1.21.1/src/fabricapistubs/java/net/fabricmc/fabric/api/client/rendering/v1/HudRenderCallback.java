package net.fabricmc.fabric.api.client.rendering.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Compile-time stand-in: Fabric API 0.116's HUD callback, in Mojang's names.
 */
public interface HudRenderCallback {
	Event<HudRenderCallback> EVENT = null;

	void onHudRender(GuiGraphics drawContext, DeltaTracker tickCounter);
}
