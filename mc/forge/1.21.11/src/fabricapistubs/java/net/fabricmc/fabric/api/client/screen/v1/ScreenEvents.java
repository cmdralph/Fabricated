package net.fabricmc.fabric.api.client.screen.v1;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import net.fabricmc.fabric.api.event.Event;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public final class ScreenEvents {
	private ScreenEvents() {
	}

	public static Event<BeforeRender> beforeRender(Screen screen) {
		throw new AssertionError("stand-in");
	}

	public static Event<AfterRender> afterRender(Screen screen) {
		throw new AssertionError("stand-in");
	}

	@FunctionalInterface
	public interface BeforeRender {
		void beforeRender(Screen screen, GuiGraphics drawContext, int mouseX, int mouseY, float tickDelta);
	}

	@FunctionalInterface
	public interface AfterRender {
		void afterRender(Screen screen, GuiGraphics drawContext, int mouseX, int mouseY, float tickDelta);
	}
}
