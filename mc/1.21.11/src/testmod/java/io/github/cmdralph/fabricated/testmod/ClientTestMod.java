package io.github.cmdralph.fabricated.testmod;

import java.lang.reflect.Method;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * Client part of the CI test mod (run by the client smoke test, which joins a singleplayer world): client
 * entrypoint, client lifecycle, a client resource reload listener, a configuration task and a networking round trip
 * with the integrated server (the server sides are {@link ConfigurationTest} and {@link NetworkingTest}).
 */
public final class ClientTestMod implements ClientModInitializer {
	private boolean menuRequested;

	@Override
	public void onInitializeClient() {
		// As on Fabric, client initializers run once the game instance exists.
		if (net.minecraft.client.Minecraft.getInstance() != null) TestMod.LOGGER.info("FABRICATED-TEST client entrypoint OK");
		else TestMod.LOGGER.error("FABRICATED-TEST client entrypoint FAILED: Minecraft.getInstance() is null");
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			TestMod.LOGGER.info("FABRICATED-TEST client started OK");
			// As OneConfig registers its font listener: the vanilla way, once NeoForge has sorted the listeners.
			if (client.getResourceManager() instanceof ReloadableResourceManager manager) {
				try {
					manager.registerReloadListener((ResourceManagerReloadListener) resources -> { });
					TestMod.LOGGER.info("FABRICATED-TEST late vanilla reload listener OK");
				} catch (UnsupportedOperationException e) {
					TestMod.LOGGER.error("FABRICATED-TEST late vanilla reload listener FAILED", e);
				}
			}
		});
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(Identifier.fromNamespaceAndPath("fabricated_testmod", "client_listener"),
				(ResourceManagerReloadListener) manager -> TestMod.LOGGER.info("FABRICATED-TEST client resource reload listener OK"));

		ClientConfigurationNetworking.registerGlobalReceiver(ConfigurationTest.Request.TYPE,
				(payload, context) -> context.responseSender().sendPacket(new ConfigurationTest.Reply(7)));
		ClientPlayNetworking.registerGlobalReceiver(NetworkingTest.Ping.TYPE, (payload, context) -> {
			if (payload.value() == 42) TestMod.LOGGER.info("FABRICATED-TEST client networking round trip OK");
		});
		// The extended menu (MenuTest): a screen for it, made public by the test mod's access widener, and a request
		// to open it once the player has been in the world for a moment.
		try {
			Method register = MenuScreens.class.getMethod("register", MenuType.class, MenuScreens.ScreenConstructor.class);
			register.invoke(null, MenuTest.TYPE, (MenuScreens.ScreenConstructor<ChestMenu, ContainerScreen>) ContainerScreen::new);
		} catch (ReflectiveOperationException e) {
			TestMod.LOGGER.error("FABRICATED-TEST could not register the test menu's screen", e);
		}
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!menuRequested && client.player != null && client.player.tickCount > 40 && ClientPlayNetworking.canSend(NetworkingTest.Ping.TYPE)) {
				menuRequested = true;
				ClientPlayNetworking.send(new NetworkingTest.Ping(MenuTest.OPEN_REQUEST));
			}
		});
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
			TestMod.LOGGER.info("FABRICATED-TEST client join event OK");
			if (ClientPlayNetworking.canSend(NetworkingTest.Ping.TYPE)) {
				ClientPlayNetworking.send(new NetworkingTest.Ping(41));
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST client cannot send the ping payload to the server");
			}
		});
	}
}
