package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Fabric API's client modules Fabricated passes through (the Fabric API jar provides them), as most mods with a client
 * use them: a key mapping in the game's options (key mapping API), HUD elements drawn (rendering API: one attached
 * after a vanilla element, which Fabricated draws from NeoForge's HUD layer for it, and one added last, which upstream's
 * subtitle overlay mixin draws), a screen's initialization and drawing events (screen API, whose drawing events
 * Fabricated fires from NeoForge's screen render events) and the client commands' registration on joining a world
 * (command API), and the lifecycle API's client stopping event. A HUD element is reported the first time it is drawn, as
 * the world's loading screen may still hide the HUD for a while after joining, and as not drawn when the game stops.
 * <p>HUD-3: elements attached to vanilla ones are drawn only where vanilla draws those: none with the GUI hidden (the
 * test hides it for two seconds once the element after chat was drawn, the one after the sleep overlay, which vanilla
 * draws with the GUI hidden too, counting the frames), none after the health bar in a game mode the player can't be
 * hurt in, and none after the experience level without experience to show.
 */
public final class ClientApiTestMod implements ClientModInitializer {
	private static final List<String> HUD_CHECKS = List.of("hud element", "hud element after chat");
	private final Set<String> drawn = new HashSet<>();
	private boolean screenInitialized;
	private boolean screenDrawn;
	private boolean commandsRegistered;
	private boolean reported;
	/** What a HUD element was drawn after, where vanilla doesn't draw that element. */
	private final Set<String> drawnWhereVanillaIsnt = new TreeSet<>();
	private int hiddenFrames;
	private int hiddenFrom = -1;
	private boolean hidingDone;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "checks"));
		KeyMapping mapping = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fabricated_testmod_frapi.check", InputConstants.UNKNOWN.getValue(), category));
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			if (Arrays.asList(client.options.keyMappings).contains(mapping)) FrapiTestMod.LOGGER.info("FABRICATED-TEST key mapping OK");
			else FrapiTestMod.LOGGER.error("FABRICATED-TEST key mapping FAILED: the game's options don't list it");
		});
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "check"), (graphics, deltaTracker) -> drawn("hud element"));
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "after_chat"), (graphics, deltaTracker) -> {
			if (Minecraft.getInstance().options.hideGui) drawnWhereVanillaIsnt.add("chat, with the GUI hidden");
			else drawn("hud element after chat");
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.SLEEP, Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "after_sleep"), (graphics, deltaTracker) -> {
			if (Minecraft.getInstance().options.hideGui) hiddenFrames++;
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HEALTH_BAR, Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "after_health"), (graphics, deltaTracker) -> {
			Minecraft client = Minecraft.getInstance();
			if (client.gameMode == null || !client.gameMode.canHurtPlayer()) drawnWhereVanillaIsnt.add("the health bar, in a game mode the player can't be hurt in");
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.EXPERIENCE_LEVEL, Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "after_experience"),
				(graphics, deltaTracker) -> {
					Minecraft client = Minecraft.getInstance();
					if (client.gameMode == null || !client.gameMode.hasExperience() || client.player == null || client.player.experienceLevel <= 0) {
						drawnWhereVanillaIsnt.add("the experience level, without experience to show");
					}
				});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			FrapiTestMod.LOGGER.info("FABRICATED-TEST client stopping OK");
			for (String check : HUD_CHECKS) {
				if (!drawn.contains(check)) report(false, check, "not drawn while the game ran");
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			screenInitialized = true;
			ScreenEvents.afterExtract(screen).register((shown, graphics, mouseX, mouseY, tickProgress) -> screenDrawn = true);
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
			dispatcher.register(LiteralArgumentBuilder.literal("fabricated_testmod_frapi"));
			commandsRegistered = true;
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player != null && !hidingDone) hideTheGuiAWhile(client);
			if (reported || client.player == null || client.player.tickCount <= 40) return;
			reported = true;
			report(screenInitialized, "screen init event", "no screen's initialization fired the event");
			report(screenDrawn, "screen draw event", "no screen's drawing fired the event");
			report(commandsRegistered, "client commands", "not registered on joining the world");
		});
	}

	/** Hides the GUI for 40 ticks once the element after chat was drawn, then reports what was drawn where vanilla's isn't. */
	private void hideTheGuiAWhile(Minecraft client) {
		int tick = client.player.tickCount;
		if (hiddenFrom < 0) {
			if (!drawn.contains("hud element after chat") || client.options.hideGui) return;
			hiddenFrom = tick;
			client.options.hideGui = true;
			return;
		}
		if (tick < hiddenFrom + 40) return;
		client.options.hideGui = false;
		hidingDone = true;
		if (hiddenFrames == 0) report(false, "hud conditions", "no frame drawn while the GUI was hidden");
		else if (!drawnWhereVanillaIsnt.isEmpty()) report(false, "hud conditions", "drawn after " + String.join("; after ", drawnWhereVanillaIsnt));
		else report(true, "hud conditions", null);
	}

	private void drawn(String check) {
		if (drawn.add(check)) report(true, check, null);
	}

	private static void report(boolean ok, String check, String failure) {
		if (ok) FrapiTestMod.LOGGER.info("FABRICATED-TEST {} OK", check);
		else FrapiTestMod.LOGGER.error("FABRICATED-TEST {} FAILED: {}", check, failure);
	}
}
