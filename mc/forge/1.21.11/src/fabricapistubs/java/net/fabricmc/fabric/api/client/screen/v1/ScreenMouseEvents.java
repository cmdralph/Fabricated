package net.fabricmc.fabric.api.client.screen.v1;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import net.fabricmc.fabric.api.event.Event;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public final class ScreenMouseEvents {
	private ScreenMouseEvents() {
	}

	public static Event<AllowMouseClick> allowMouseClick(Screen screen) {
		throw new AssertionError("stand-in");
	}

	public static Event<BeforeMouseClick> beforeMouseClick(Screen screen) {
		throw new AssertionError("stand-in");
	}

	public static Event<AfterMouseClick> afterMouseClick(Screen screen) {
		throw new AssertionError("stand-in");
	}

	public static Event<AllowMouseRelease> allowMouseRelease(Screen screen) {
		throw new AssertionError("stand-in");
	}

	public static Event<BeforeMouseRelease> beforeMouseRelease(Screen screen) {
		throw new AssertionError("stand-in");
	}

	public static Event<AfterMouseRelease> afterMouseRelease(Screen screen) {
		throw new AssertionError("stand-in");
	}

	public static Event<AllowMouseDrag> allowMouseDrag(Screen screen) {
		throw new AssertionError("stand-in");
	}

	public static Event<BeforeMouseDrag> beforeMouseDrag(Screen screen) {
		throw new AssertionError("stand-in");
	}

	public static Event<AfterMouseDrag> afterMouseDrag(Screen screen) {
		throw new AssertionError("stand-in");
	}

	@FunctionalInterface
	public interface AllowMouseClick {
		boolean allowMouseClick(Screen screen, MouseButtonEvent context);
	}

	@FunctionalInterface
	public interface BeforeMouseClick {
		void beforeMouseClick(Screen screen, MouseButtonEvent context);
	}

	@FunctionalInterface
	public interface AfterMouseClick {
		boolean afterMouseClick(Screen screen, MouseButtonEvent context, boolean consumed);
	}

	@FunctionalInterface
	public interface AllowMouseRelease {
		boolean allowMouseRelease(Screen screen, MouseButtonEvent context);
	}

	@FunctionalInterface
	public interface BeforeMouseRelease {
		void beforeMouseRelease(Screen screen, MouseButtonEvent context);
	}

	@FunctionalInterface
	public interface AfterMouseRelease {
		boolean afterMouseRelease(Screen screen, MouseButtonEvent context, boolean consumed);
	}

	@FunctionalInterface
	public interface AllowMouseDrag {
		boolean allowMouseDrag(Screen screen, MouseButtonEvent context, double horizontalAmount, double verticalAmount);
	}

	@FunctionalInterface
	public interface BeforeMouseDrag {
		void beforeMouseDrag(Screen screen, MouseButtonEvent context, double horizontalAmount, double verticalAmount);
	}

	@FunctionalInterface
	public interface AfterMouseDrag {
		boolean afterMouseDrag(Screen screen, MouseButtonEvent context, double horizontalAmount, double verticalAmount, boolean consumed);
	}
}
