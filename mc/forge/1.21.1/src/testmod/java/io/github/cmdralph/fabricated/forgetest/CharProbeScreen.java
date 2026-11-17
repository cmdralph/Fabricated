package io.github.cmdralph.fabricated.forgetest;

import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The screen the char typed wrapper check ({@code ScreenCharTypedMixin}) shows the keyboard handler while it sends its
 * characters, never opened: it counts the characters that reach it, and those the check's wrapper saw with it as the
 * listener, so no screen the game shows gets them. It keeps the check's state, which the mixin's handlers keep none of
 * their own of, as a mod's wrapper keeps none with its other hooks.
 */
public final class CharProbeScreen extends Screen {
	/** The characters the check sends: one char, then a pair (U+1F600), which go through the two lambdas. */
	private static final int SENT = 3;
	/** The probe shown while the characters are sent, null otherwise; render thread only. */
	private static CharProbeScreen sending;
	private static boolean sent;
	private final Screen shown;
	private int received;
	private int wrapped;

	private CharProbeScreen(Screen shown) {
		super(Component.empty());
		this.shown = shown;
	}

	/** Whether the check has sent its characters. */
	public static boolean sent() {
		return sent;
	}

	/** Shows a probe to the keyboard handler in place of the shown screen, until {@link #hide}. */
	public static CharProbeScreen show(Minecraft client) {
		sent = true;
		sending = new CharProbeScreen(client.screen);
		client.screen = sending;
		return sending;
	}

	/** Puts the shown screen back. */
	public void hide(Minecraft client) {
		client.screen = shown;
		sending = null;
	}

	/** From the wrapper, before its original call: whether it was made on the probe shown. */
	public static void wrapped(GuiEventListener listener) {
		if (sending != null && listener == sending) sending.wrapped++;
	}

	@Override
	public boolean charTyped(char typed, int modifiers) {
		received++;
		return true;
	}

	/** Logs whether the wrapper saw each character with the probe, and each reached it. */
	public void report(Logger logger) {
		if (wrapped == SENT && received == SENT) {
			logger.info("FABRICATED-TEST screen char typed wrapper OK");
		} else {
			logger.error("FABRICATED-TEST screen char typed wrapper FAILED: of the {} characters sent, the wrapper saw {} with the screen, which got {}",
					SENT, wrapped, received);
		}
	}
}
