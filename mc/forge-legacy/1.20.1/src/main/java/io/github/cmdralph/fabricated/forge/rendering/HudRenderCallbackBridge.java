package io.github.cmdralph.fabricated.forge.rendering;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Fabric API's {@code HudRenderCallback} ({@code fabric-rendering-v1}, passed through) on Forge 47 (HUD-2), from
 * Forge's {@link RenderGuiEvent.Post}. Forge's GUI ({@code ForgeGui}) draws the HUD from its overlays in place of
 * vanilla's {@code Gui.render}, which it overrides and never calls, so upstream's mixin, which fires the callback at the
 * end of vanilla's method, applies to code that doesn't run: a Fabric mod's HUD drawn through the callback was never
 * drawn. Forge posts the event at the end of its own HUD drawing; Fabric's callback runs there, before Forge mods'
 * listeners. A Forge mod that cancels the HUD's {@code Pre} event hides all of the HUD, the callback's drawing too.
 */
public final class HudRenderCallbackBridge {
	private HudRenderCallbackBridge() {
	}

	/** Called once on the client; nothing is registered without Fabric API's rendering module. */
	public static void register() {
		if (FabricLoader.getInstance().isModLoaded("fabric-rendering-v1")) Upstream.register();
	}

	/** Everything that names Fabric's rendering module: loaded only once it is known to be installed. */
	private static final class Upstream {
		private Upstream() {
		}

		static void register() {
			MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, RenderGuiEvent.Post.class,
					event -> HudRenderCallback.EVENT.invoker().onHudRender(event.getGuiGraphics(), event.getPartialTick()));
		}
	}
}
