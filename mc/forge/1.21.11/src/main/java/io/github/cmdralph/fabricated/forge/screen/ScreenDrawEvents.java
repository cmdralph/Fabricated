package io.github.cmdralph.fabricated.forge.screen;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.listener.Priority;

/**
 * Fabric API's {@code ScreenEvents.beforeRender} and {@code afterRender} ({@code fabric-screen-api-v1}, passed through)
 * on Forge 61, from Forge's {@link ScreenEvent.Render} events, which Forge posts around the screen's drawing where
 * upstream's wrapper sat: Forge's game renderer no longer calls the screen itself but through its own hook, so the plan
 * leaves upstream's wrapper out. Fabric's events run right around the screen: after Forge mods' {@code Pre} listeners
 * (and only when none cancelled the drawing), before their {@code Post} listeners.
 */
public final class ScreenDrawEvents {
	private ScreenDrawEvents() {
	}

	/** Called once on the client; nothing is registered without Fabric API's screen module. */
	public static void register() {
		if (FabricLoader.getInstance().isModLoaded("fabric-screen-api-v1")) Upstream.register();
	}

	/** Everything that names Fabric's screen module: loaded only once it is known to be installed. */
	private static final class Upstream {
		private Upstream() {
		}

		static void register() {
			ScreenEvent.Render.Pre.BUS.addListener(Priority.LOWEST, (ScreenEvent.Render.Pre event) -> ScreenEvents.beforeRender(event.getScreen()).invoker()
					.beforeRender(event.getScreen(), event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick()));
			ScreenEvent.Render.Post.BUS.addListener(Priority.HIGHEST, (ScreenEvent.Render.Post event) -> ScreenEvents.afterRender(event.getScreen()).invoker()
					.afterRender(event.getScreen(), event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick()));
		}
	}
}
