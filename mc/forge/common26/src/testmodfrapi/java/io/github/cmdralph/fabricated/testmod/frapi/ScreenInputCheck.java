package io.github.cmdralph.fabricated.testmod.frapi;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.cmdralph.fabricated.testmod.frapi.forgemixin.KeyboardHandlerInvoker;
import io.github.cmdralph.fabricated.testmod.frapi.forgemixin.MouseHandlerInvoker;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;

/**
 * Fabric's screen input events on Forge, which moved the screen's input calls out of the game's keyboard and mouse
 * handlers into its own hooks (Fabricated fires the events from there: SCREEN-1). Synthetic input goes through the
 * handlers' entry points, as the window's callbacks and Fabric's client game tests call them, to whichever screen is
 * shown: a key press, which the check's {@code allowKeyPress} listener refuses (so the screen must not get it, and
 * {@code beforeKeyPress} must not fire), the key's release, which must reach {@code afterKeyRelease}, a typed character
 * and a mouse button's press and release, which the {@code allowCharType}, {@code allowMouseClick} and
 * {@code allowMouseRelease} listeners refuse. Every input the check sends is refused except the release, so no screen
 * acts on it: a mouse button no widget answers (button 8), a key and a character no screen acts on. The listeners
 * answer only the check's own input. Sent until each listener saw it, as the game's loading overlay holds the mouse
 * and typed characters back from the screen behind it.
 */
final class ScreenInputCheck {
	/** The window callbacks' actions (GLFW's, which Minecraft keeps as its own). */
	private static final int PRESS = 1;
	private static final int RELEASE = 0;
	/** Button 8: no widget answers it (they take the left button only). */
	private static final int BUTTON = 7;
	private static final KeyEvent KEY = new KeyEvent(InputConstants.KEY_LSHIFT, 0, 0);
	private static final CharacterEvent CHARACTER = new CharacterEvent('x');
	/** Ticks between two sends, and sends at most: past those, the report says what never arrived. */
	private static final int SEND_TICKS = 5;
	private static final int MAX_SENDS = 200;

	// Render thread only.
	private boolean keyPressRefused;
	private boolean keyPressWentOn;
	private boolean keyReleased;
	private boolean characterRefused;
	private boolean clickRefused;
	private boolean releaseRefused;
	private int ticks;
	private int sends;

	/** The listeners, on every screen when it is initialized, so whichever is shown when the input is sent has them. */
	void listen(Screen screen) {
		ScreenKeyboardEvents.allowKeyPress(screen).register((shown, event) -> {
			if (!KEY.equals(event)) return true;
			keyPressRefused = true;
			return false;
		});
		ScreenKeyboardEvents.beforeKeyPress(screen).register((shown, event) -> {
			if (KEY.equals(event)) keyPressWentOn = true;
		});
		ScreenKeyboardEvents.afterKeyRelease(screen).register((shown, event) -> {
			if (KEY.equals(event)) keyReleased = true;
		});
		ScreenKeyboardEvents.allowCharType(screen).register((shown, event) -> {
			if (!CHARACTER.equals(event)) return true;
			characterRefused = true;
			return false;
		});
		ScreenMouseEvents.allowMouseClick(screen).register((shown, event) -> {
			if (event.button() != BUTTON) return true;
			clickRefused = true;
			return false;
		});
		ScreenMouseEvents.allowMouseRelease(screen).register((shown, event) -> {
			if (event.button() != BUTTON) return true;
			releaseRefused = true;
			return false;
		});
	}

	/** Each client tick before the player is in the world, where a screen is always shown: sends the input now and then. */
	void tick(Minecraft client) {
		if (done() || sends >= MAX_SENDS || ticks++ % SEND_TICKS != 0) return;
		sends++;
		long window = client.getWindow().handle();
		KeyboardHandlerInvoker keyboard = (KeyboardHandlerInvoker) client.keyboardHandler;
		MouseHandlerInvoker mouse = (MouseHandlerInvoker) client.mouseHandler;
		if (!keyPressRefused) keyboard.invokeKeyPress(window, PRESS, KEY);
		if (!keyReleased) keyboard.invokeKeyPress(window, RELEASE, KEY);
		if (!characterRefused) keyboard.invokeCharTyped(window, CHARACTER);
		if (!clickRefused) mouse.invokeOnButton(window, new MouseButtonInfo(BUTTON, 0), PRESS);
		if (!releaseRefused) mouse.invokeOnButton(window, new MouseButtonInfo(BUTTON, 0), RELEASE);
	}

	private boolean done() {
		return keyPressRefused && keyReleased && characterRefused && clickRefused && releaseRefused;
	}

	void report() {
		report(keyPressRefused && !keyPressWentOn, "screen key press event", keyPressWentOn
				? "a key press allowKeyPress refused still went on to beforeKeyPress"
				: "allowKeyPress never saw the key press sent to the shown screen");
		report(keyReleased, "screen key release event", "afterKeyRelease never saw the key release sent to the shown screen");
		report(characterRefused, "screen character event", "allowCharType never saw the character typed into the shown screen");
		report(clickRefused, "screen mouse click event", "allowMouseClick never saw the button pressed over the shown screen");
		report(releaseRefused, "screen mouse release event", "allowMouseRelease never saw the button released over the shown screen");
	}

	private static void report(boolean ok, String check, String failure) {
		if (ok) FrapiTestMod.LOGGER.info("FABRICATED-TEST {} OK", check);
		else FrapiTestMod.LOGGER.error("FABRICATED-TEST {} FAILED: {}", check, failure);
	}
}
