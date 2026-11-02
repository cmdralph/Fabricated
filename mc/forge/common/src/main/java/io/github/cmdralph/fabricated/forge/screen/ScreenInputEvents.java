package io.github.cmdralph.fabricated.forge.screen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Fabric API's screen input events ({@code fabric-screen-api-v1}, passed through) on Forge 61 to 66, around the same
 * calls upstream wraps. Forge's input handlers no longer call a screen's {@code keyPressed}, {@code keyReleased},
 * {@code mouseClicked}, {@code mouseReleased} and {@code mouseDragged} themselves: each moved into a Forge hook that posts
 * Forge's screen events around it ({@code ForgeHooksClient.onScreenKeyPressed}, {@code onScreenKeyReleased},
 * {@code onScreenMouseDrag}, {@code ForgeEventFactoryClient.onScreenMouseClicked}, {@code onScreenMouseReleased}). The
 * plan leaves upstream's wrappers of the vanilla calls out, and Fabricated's mixins on those hooks wrap the calls there
 * instead, as upstream does: Fabric's events run inside Forge's, right around the screen's own handling, and a press
 * Fabric's allow event refuses counts as handled. The scroll wrapper still applies (Forge's handler keeps that call).
 */
public final class ScreenInputEvents {
	/** Fabric API's screen module is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-screen-api-v1");

	private ScreenInputEvents() {
	}

	public static boolean keyPressed(Screen screen, KeyEvent event, Operation<Boolean> keyPressed) {
		return ENABLED ? Upstream.keyPressed(screen, event, keyPressed) : keyPressed.call(screen, event);
	}

	public static boolean keyReleased(Screen screen, KeyEvent event, Operation<Boolean> keyReleased) {
		return ENABLED ? Upstream.keyReleased(screen, event, keyReleased) : keyReleased.call(screen, event);
	}

	public static boolean mouseClicked(Screen screen, MouseButtonEvent event, boolean doubleClick, Operation<Boolean> mouseClicked) {
		return ENABLED ? Upstream.mouseClicked(screen, event, doubleClick, mouseClicked) : mouseClicked.call(screen, event, doubleClick);
	}

	public static boolean mouseReleased(Screen screen, MouseButtonEvent event, Operation<Boolean> mouseReleased) {
		return ENABLED ? Upstream.mouseReleased(screen, event, mouseReleased) : mouseReleased.call(screen, event);
	}

	public static boolean mouseDragged(Screen screen, MouseButtonEvent event, double dragX, double dragY, Operation<Boolean> mouseDragged) {
		return ENABLED ? Upstream.mouseDragged(screen, event, dragX, dragY, mouseDragged) : mouseDragged.call(screen, event, dragX, dragY);
	}

	/**
	 * Everything that names Fabric's screen module, upstream's wrappers as they are: loaded only once {@link #ENABLED}
	 * is known to be true. The screen passed to the events is the one the call is made on, whatever the handlers do.
	 */
	private static final class Upstream {
		private Upstream() {
		}

		static boolean keyPressed(Screen screen, KeyEvent event, Operation<Boolean> keyPressed) {
			if (screen != null) {
				if (!ScreenKeyboardEvents.allowKeyPress(screen).invoker().allowKeyPress(screen, event)) return true;
				ScreenKeyboardEvents.beforeKeyPress(screen).invoker().beforeKeyPress(screen, event);
			}
			boolean result = keyPressed.call(screen, event);
			if (screen != null) ScreenKeyboardEvents.afterKeyPress(screen).invoker().afterKeyPress(screen, event);
			return result;
		}

		static boolean keyReleased(Screen screen, KeyEvent event, Operation<Boolean> keyReleased) {
			if (screen != null) {
				if (!ScreenKeyboardEvents.allowKeyRelease(screen).invoker().allowKeyRelease(screen, event)) return true;
				ScreenKeyboardEvents.beforeKeyRelease(screen).invoker().beforeKeyRelease(screen, event);
			}
			boolean result = keyReleased.call(screen, event);
			if (screen != null) ScreenKeyboardEvents.afterKeyRelease(screen).invoker().afterKeyRelease(screen, event);
			return result;
		}

		static boolean mouseClicked(Screen screen, MouseButtonEvent event, boolean doubleClick, Operation<Boolean> mouseClicked) {
			if (screen != null) {
				if (!ScreenMouseEvents.allowMouseClick(screen).invoker().allowMouseClick(screen, event)) return true;
				ScreenMouseEvents.beforeMouseClick(screen).invoker().beforeMouseClick(screen, event);
			}
			boolean result = mouseClicked.call(screen, event, doubleClick);
			if (screen != null) result |= ScreenMouseEvents.afterMouseClick(screen).invoker().afterMouseClick(screen, event, result);
			return result;
		}

		static boolean mouseReleased(Screen screen, MouseButtonEvent event, Operation<Boolean> mouseReleased) {
			if (screen != null) {
				if (!ScreenMouseEvents.allowMouseRelease(screen).invoker().allowMouseRelease(screen, event)) return true;
				ScreenMouseEvents.beforeMouseRelease(screen).invoker().beforeMouseRelease(screen, event);
			}
			boolean result = mouseReleased.call(screen, event);
			if (screen != null) result |= ScreenMouseEvents.afterMouseRelease(screen).invoker().afterMouseRelease(screen, event, result);
			return result;
		}

		static boolean mouseDragged(Screen screen, MouseButtonEvent event, double dragX, double dragY, Operation<Boolean> mouseDragged) {
			if (screen != null) {
				if (!ScreenMouseEvents.allowMouseDrag(screen).invoker().allowMouseDrag(screen, event, dragX, dragY)) return true;
				ScreenMouseEvents.beforeMouseDrag(screen).invoker().beforeMouseDrag(screen, event, dragX, dragY);
			}
			boolean result = mouseDragged.call(screen, event, dragX, dragY);
			if (screen != null) result |= ScreenMouseEvents.afterMouseDrag(screen).invoker().afterMouseDrag(screen, event, dragX, dragY, result);
			return result;
		}
	}
}
