package io.github.cmdralph.fabricated.neoforge.rendering;

import net.minecraft.client.Minecraft;

/**
 * The version-specific part of {@link HudBridge} (26.2+): whether the player hid the GUI (F1), which 26.2 moved from
 * the options to the game's {@code Hud}.
 */
final class HudCompat {
	private HudCompat() {
	}

	static boolean guiHidden() {
		return Minecraft.getInstance().gui.hud.isHidden();
	}
}
