package io.github.cmdralph.fabricated.forgetest;

import java.util.HashSet;
import java.util.Set;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.resources.Identifier;

/**
 * The forge test mod's part for fabric-rendering-v1's HUD on Forge 61 (HUD-2; with Fabric API's jar only). Forge draws
 * the HUD as layers of its own, which Fabricated's in-game mod draws the Fabric elements attached to vanilla ones from,
 * and after which it fires the deprecated {@code HudRenderCallback}: an element attached after chat, one attached before
 * the hotbar and the callback must each be drawn or fired once the world shows. Upstream's own hook still fires the
 * callback while a world is loading (Forge draws no layer then), so only a call outside the loading screen counts.
 * Each is reported the first time; the client smoke reports one never reported as missing. (On 26.x the client API test
 * mod checks the HUD.)
 */
@Environment(EnvType.CLIENT)
public final class ForgeHudTest {
	/** Drawn on the render thread only. */
	private static final Set<String> DRAWN = new HashSet<>();

	private ForgeHudTest() {
	}

	/** From the client entrypoint, with Fabric API's rendering module. */
	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(ForgeTestMod.MOD_ID, "after_chat"),
				(graphics, tickCounter) -> drawn("hud element after chat"));
		HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath(ForgeTestMod.MOD_ID, "before_hotbar"),
				(graphics, tickCounter) -> drawn("hud element before the hotbar"));
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
			if (!(Minecraft.getInstance().screen instanceof LevelLoadingScreen)) drawn("hud render callback");
		});
	}

	private static void drawn(String check) {
		if (DRAWN.add(check)) ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST {} OK", check);
	}
}
