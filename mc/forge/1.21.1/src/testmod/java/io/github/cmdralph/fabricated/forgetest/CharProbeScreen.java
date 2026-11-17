package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The screen the char typed wrapper check ({@code ScreenCharTypedMixin}) shows the keyboard handler while it sends its
 * characters, never opened: it counts the characters that reach it, so no screen the game shows gets them.
 */
public final class CharProbeScreen extends Screen {
	private int received;

	public CharProbeScreen() {
		super(Component.empty());
	}

	@Override
	public boolean charTyped(char typed, int modifiers) {
		received++;
		return true;
	}

	/** How many characters reached the screen. */
	public int received() {
		return received;
	}
}
