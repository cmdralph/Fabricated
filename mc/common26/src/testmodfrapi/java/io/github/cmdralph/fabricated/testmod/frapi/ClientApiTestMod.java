package io.github.cmdralph.fabricated.testmod.frapi;

import java.util.Arrays;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Fabric API's client modules Fabricated passes through (the Fabric API jar provides them), as most mods with a client
 * use them: a key mapping in the game's options (key mapping API), a HUD element drawn (rendering API, whose HUD
 * Fabricated draws from NeoForge's HUD layers), a screen's initialization and drawing events (screen API, whose drawing
 * events Fabricated fires from NeoForge's screen render events) and the client commands' registration on joining a world
 * (command API).
 */
public final class ClientApiTestMod implements ClientModInitializer {
	private int hudDraws;
	private boolean screenInitialized;
	private boolean screenDrawn;
	private boolean commandsRegistered;
	private boolean reported;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "checks"));
		KeyMapping mapping = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fabricated_testmod_frapi.check", InputConstants.UNKNOWN.getValue(), category));
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			if (Arrays.asList(client.options.keyMappings).contains(mapping)) FrapiTestMod.LOGGER.info("FABRICATED-TEST key mapping OK");
			else FrapiTestMod.LOGGER.error("FABRICATED-TEST key mapping FAILED: the game's options don't list it");
		});
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "check"), (graphics, deltaTracker) -> hudDraws++);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			screenInitialized = true;
			ScreenEvents.afterExtract(screen).register((drawn, graphics, mouseX, mouseY, tickProgress) -> screenDrawn = true);
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
			dispatcher.register(LiteralArgumentBuilder.literal("fabricated_testmod_frapi"));
			commandsRegistered = true;
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (reported || client.player == null || client.player.tickCount <= 40) return;
			reported = true;
			report(hudDraws > 0, "hud element", "not drawn in the world");
			report(screenInitialized, "screen init event", "no screen's initialization fired the event");
			report(screenDrawn, "screen draw event", "no screen's drawing fired the event");
			report(commandsRegistered, "client commands", "not registered on joining the world");
		});
	}

	private static void report(boolean ok, String check, String failure) {
		if (ok) FrapiTestMod.LOGGER.info("FABRICATED-TEST {} OK", check);
		else FrapiTestMod.LOGGER.error("FABRICATED-TEST {} FAILED: {}", check, failure);
	}
}
