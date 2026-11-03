package io.github.cmdralph.fabricated.neoforge.rendering;

import net.minecraft.client.Minecraft;

/** The version-specific part of {@link HudBridge} (1.21.x and 26.1): whether the player hid the GUI (F1). */
final class HudCompat {
	private HudCompat() {
	}

	static boolean guiHidden() {
		return Minecraft.getInstance().options.hideGui;
	}
}
