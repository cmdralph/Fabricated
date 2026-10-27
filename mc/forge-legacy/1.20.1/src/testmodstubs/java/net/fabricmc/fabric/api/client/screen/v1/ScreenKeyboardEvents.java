package net.fabricmc.fabric.api.client.screen.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.class_437;

public final class ScreenKeyboardEvents {
	private ScreenKeyboardEvents() {
	}

	public static Event<AllowKeyPress> allowKeyPress(class_437 screen) {
		throw new AssertionError("stand-in");
	}

	public static Event<AllowKeyRelease> allowKeyRelease(class_437 screen) {
		throw new AssertionError("stand-in");
	}

	@FunctionalInterface
	public interface AllowKeyPress {
		boolean allowKeyPress(class_437 screen, int key, int scancode, int modifiers);
	}

	@FunctionalInterface
	public interface AllowKeyRelease {
		boolean allowKeyRelease(class_437 screen, int key, int scancode, int modifiers);
	}
}
