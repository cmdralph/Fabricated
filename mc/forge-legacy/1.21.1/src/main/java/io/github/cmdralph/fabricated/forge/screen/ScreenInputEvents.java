package io.github.cmdralph.fabricated.forge.screen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

/**
 * Fabric API's screen mouse click and release events ({@code fabric-screen-api-v1}, passed through) on Forge 52, around
 * the same calls upstream wraps (LAMBDA-10). Forge 52's mouse handler no longer calls a screen's {@code mouseClicked}
 * and {@code mouseReleased} in its lambdas: each moved into Forge's event factory, which posts Forge's screen events
 * around it ({@code ForgeEventFactoryClient.onScreenMouseClicked}, {@code onScreenMouseReleased}), so pre-flight left
 * upstream's mouse hooks out, the scroll ones with them, as they share a field. The plan leaves upstream's click and
 * release hooks out, so its scroll hooks apply, and Fabricated's mixin on Forge's event factory wraps the calls there
 * instead, as on Forge 61 to 66: Fabric's events run inside Forge's, right around the screen's own handling, and a
 * click Fabric's allow event refuses counts as handled. Pre-flight points upstream's key hooks at the call of Forge's
 * key hooks, which make the screen's key calls.
 */
public final class ScreenInputEvents {
	/** Fabric API's screen module is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-screen-api-v1");

	private ScreenInputEvents() {
	}

	public static boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button, Operation<Boolean> mouseClicked) {
		return ENABLED ? Upstream.mouseClicked(screen, mouseX, mouseY, button, mouseClicked) : mouseClicked.call(screen, mouseX, mouseY, button);
	}

	public static boolean mouseReleased(Screen screen, double mouseX, double mouseY, int button, Operation<Boolean> mouseReleased) {
		return ENABLED ? Upstream.mouseReleased(screen, mouseX, mouseY, button, mouseReleased) : mouseReleased.call(screen, mouseX, mouseY, button);
	}

	/**
	 * Everything that names Fabric's screen module, upstream's wrappers as they are: loaded only once {@link #ENABLED}
	 * is known to be true. The screen passed to the events is the one the call is made on, whatever the handlers do.
	 */
	private static final class Upstream {
		private Upstream() {
		}

		static boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button, Operation<Boolean> mouseClicked) {
			if (screen != null) {
				if (!ScreenMouseEvents.allowMouseClick(screen).invoker().allowMouseClick(screen, mouseX, mouseY, button)) return true;
				ScreenMouseEvents.beforeMouseClick(screen).invoker().beforeMouseClick(screen, mouseX, mouseY, button);
			}
			boolean result = mouseClicked.call(screen, mouseX, mouseY, button);
			if (screen != null) ScreenMouseEvents.afterMouseClick(screen).invoker().afterMouseClick(screen, mouseX, mouseY, button);
			return result;
		}

		static boolean mouseReleased(Screen screen, double mouseX, double mouseY, int button, Operation<Boolean> mouseReleased) {
			if (screen != null) {
				if (!ScreenMouseEvents.allowMouseRelease(screen).invoker().allowMouseRelease(screen, mouseX, mouseY, button)) return true;
				ScreenMouseEvents.beforeMouseRelease(screen).invoker().beforeMouseRelease(screen, mouseX, mouseY, button);
			}
			boolean result = mouseReleased.call(screen, mouseX, mouseY, button);
			if (screen != null) ScreenMouseEvents.afterMouseRelease(screen).invoker().afterMouseRelease(screen, mouseX, mouseY, button);
			return result;
		}
	}
}
