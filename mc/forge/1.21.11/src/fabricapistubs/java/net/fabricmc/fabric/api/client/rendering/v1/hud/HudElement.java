package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Compile-time stand-in: Fabric API's 1.21.11 interface, which the forge test mod implements, in Mojang's names.
 */
@FunctionalInterface
public interface HudElement {
	void render(GuiGraphics context, DeltaTracker tickCounter);
}
