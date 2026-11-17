package io.github.cmdralph.fabricated.forge.rendering;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric API's {@code HudRenderCallback} (fabric-rendering-v1, passed through) where Forge 43's GUI ends its HUD
 * drawing (HUD-2, HUD-4). Fabric API 0.77 fires it from a hook before the last call of vanilla's HUD method
 * ({@code Gui.render}), which Forge's GUI overrides and never calls: no seam takes a hook at a call inside the method,
 * so Fabricated's Fabric API plan leaves upstream's hook out and its seams' mixin fires the callback in its place, just
 * before the end seam, as upstream's runs just before vanilla's method returns.
 */
public final class FabricHudCallback {
	/** Fabric API's rendering module is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-rendering-v1");

	private FabricHudCallback() {
	}

	public static void fire(PoseStack poseStack, float partialTick) {
		if (ENABLED) Upstream.fire(poseStack, partialTick);
	}

	/** Everything that names Fabric's rendering module: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Upstream {
		private Upstream() {
		}

		static void fire(PoseStack poseStack, float partialTick) {
			HudRenderCallback.EVENT.invoker().onHudRender(poseStack, partialTick);
		}
	}
}
