package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.class_332;

import net.fabricmc.fabric.api.event.Event;

public interface HudRenderCallback {
	Event<HudRenderCallback> EVENT = null;

	void onHudRender(class_332 drawContext, float tickDelta);
}
