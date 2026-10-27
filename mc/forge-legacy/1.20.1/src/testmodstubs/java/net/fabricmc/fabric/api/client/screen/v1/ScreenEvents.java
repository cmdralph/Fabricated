package net.fabricmc.fabric.api.client.screen.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.class_310;
import net.minecraft.class_437;

public final class ScreenEvents {
	public static final Event<AfterInit> AFTER_INIT = null;

	private ScreenEvents() {
	}

	public static Event<AfterTick> afterTick(class_437 screen) {
		throw new AssertionError("stand-in");
	}

	@FunctionalInterface
	public interface AfterInit {
		void afterInit(class_310 client, class_437 screen, int scaledWidth, int scaledHeight);
	}

	@FunctionalInterface
	public interface AfterTick {
		void afterTick(class_437 screen);
	}
}
