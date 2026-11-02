package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.client.gui.GuiGraphics;

import net.fabricmc.fabric.api.event.Event;

/**
 * Compile-time stand-in: the members of Fabric API's 1.20.1 interface that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public interface HudRenderCallback {
	Event<HudRenderCallback> EVENT = null;

	void onHudRender(GuiGraphics drawContext, float tickDelta);
}
