package net.fabricmc.fabric.api.client.screen.v1;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

import net.fabricmc.fabric.api.event.Event;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public final class ScreenKeyboardEvents {
	private ScreenKeyboardEvents() {
	}

	public static Event<AllowKeyPress> allowKeyPress(Screen screen) {
		throw new AssertionError("stand-in");
	}

	public static Event<BeforeKeyPress> beforeKeyPress(Screen screen) {
		throw new AssertionError("stand-in");
	}

	public static Event<AfterKeyPress> afterKeyPress(Screen screen) {
		throw new AssertionError("stand-in");
	}

	public static Event<AllowKeyRelease> allowKeyRelease(Screen screen) {
		throw new AssertionError("stand-in");
	}

	public static Event<BeforeKeyRelease> beforeKeyRelease(Screen screen) {
		throw new AssertionError("stand-in");
	}

	public static Event<AfterKeyRelease> afterKeyRelease(Screen screen) {
		throw new AssertionError("stand-in");
	}

	@FunctionalInterface
	public interface AllowKeyPress {
		boolean allowKeyPress(Screen screen, KeyEvent context);
	}

	@FunctionalInterface
	public interface BeforeKeyPress {
		void beforeKeyPress(Screen screen, KeyEvent context);
	}

	@FunctionalInterface
	public interface AfterKeyPress {
		void afterKeyPress(Screen screen, KeyEvent context);
	}

	@FunctionalInterface
	public interface AllowKeyRelease {
		boolean allowKeyRelease(Screen screen, KeyEvent context);
	}

	@FunctionalInterface
	public interface BeforeKeyRelease {
		void beforeKeyRelease(Screen screen, KeyEvent context);
	}

	@FunctionalInterface
	public interface AfterKeyRelease {
		void afterKeyRelease(Screen screen, KeyEvent context);
	}
}
