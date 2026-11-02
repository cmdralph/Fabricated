package io.github.cmdralph.fabricated.forge.screen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;

/**
 * Fabric API's character typing events on Forge 64 to 66 (26.x's {@code fabric-screen-api-v1}, passed through), as
 * {@link ScreenInputEvents} fires its key events: around the screen's {@code charTyped} call, which Forge moved from
 * the keyboard handler into {@code ForgeHooksClient.onScreenCharTyped}.
 */
public final class ScreenCharEvents {
	/** Fabric API's screen module is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-screen-api-v1");

	private ScreenCharEvents() {
	}

	public static boolean charTyped(Screen screen, CharacterEvent event, Operation<Boolean> charTyped) {
		return ENABLED ? Upstream.charTyped(screen, event, charTyped) : charTyped.call(screen, event);
	}

	/** Everything that names Fabric's screen module, upstream's wrapper as it is: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Upstream {
		private Upstream() {
		}

		static boolean charTyped(Screen screen, CharacterEvent event, Operation<Boolean> charTyped) {
			if (screen != null) {
				if (!ScreenKeyboardEvents.allowCharType(screen).invoker().allowCharType(screen, event)) return true;
				ScreenKeyboardEvents.beforeCharType(screen).invoker().beforeCharType(screen, event);
			}
			boolean result = charTyped.call(screen, event);
			if (screen != null) ScreenKeyboardEvents.afterCharType(screen).invoker().afterCharType(screen, event);
			return result;
		}
	}
}
