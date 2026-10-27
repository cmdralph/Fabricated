package net.fabricmc.fabric.api.client.screen.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.class_437;

public final class ScreenMouseEvents {
	private ScreenMouseEvents() {
	}

	public static Event<AllowMouseClick> allowMouseClick(class_437 screen) {
		throw new AssertionError("stand-in");
	}

	public static Event<AllowMouseRelease> allowMouseRelease(class_437 screen) {
		throw new AssertionError("stand-in");
	}

	@FunctionalInterface
	public interface AllowMouseClick {
		boolean allowMouseClick(class_437 screen, double mouseX, double mouseY, int button);
	}

	@FunctionalInterface
	public interface AllowMouseRelease {
		boolean allowMouseRelease(class_437 screen, double mouseX, double mouseY, int button);
	}
}
