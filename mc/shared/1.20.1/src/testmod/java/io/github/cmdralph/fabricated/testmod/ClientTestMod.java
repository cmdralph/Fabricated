package io.github.cmdralph.fabricated.testmod;

import java.lang.reflect.Method;
import java.util.Arrays;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * Client part of the CI test mod on 1.20.1 (run by the client smoke test, which joins a singleplayer world): client
 * entrypoint, client lifecycle, a client resource reload listener and a networking round trip with the integrated
 * server (the server side is {@link NetworkingTest}). 1.20.1 has no configuration phase.
 */
public final class ClientTestMod implements ClientModInitializer {
	private boolean menuRequested;
	private boolean vanillaPingSent;

	@Override
	public void onInitializeClient() {
		// As on Fabric, client initializers run once the game instance exists.
		if (net.minecraft.client.Minecraft.getInstance() != null) TestMod.LOGGER.info("FABRICATED-TEST client entrypoint OK");
		else TestMod.LOGGER.error("FABRICATED-TEST client entrypoint FAILED: Minecraft.getInstance() is null");
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			TestMod.LOGGER.info("FABRICATED-TEST client started OK");
			// As OneConfig registers its font listener: the vanilla way, once the platform has sorted the listeners.
			if (client.getResourceManager() instanceof ReloadableResourceManager manager) {
				try {
					manager.registerReloadListener((ResourceManagerReloadListener) resources -> { });
					TestMod.LOGGER.info("FABRICATED-TEST late vanilla reload listener OK");
				} catch (UnsupportedOperationException e) {
					TestMod.LOGGER.error("FABRICATED-TEST late vanilla reload listener FAILED", e);
				}
			}
		});
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return TestCompat.id("fabricated_testmod", "client_listener");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				TestMod.LOGGER.info("FABRICATED-TEST client resource reload listener OK");
			}
		});

		if (NetworkingTest.OWN) {
			ClientPlayNetworking.registerGlobalReceiver(NetworkingTest.VanillaPing.TYPE, (packet, player, sender) -> {
				if (packet.value() == 12) TestMod.LOGGER.info("FABRICATED-TEST minecraft namespace payload round trip OK");
			});
		} else {
			TestMod.LOGGER.info("FABRICATED-TEST minecraft namespace payload round trip SKIPPED: {}", NetworkingTest.ANOTHER_MODS);
		}
		ClientPlayNetworking.registerGlobalReceiver(NetworkingTest.Ping.TYPE, (packet, player, sender) -> {
			if (packet.value() == 42) TestMod.LOGGER.info("FABRICATED-TEST client networking round trip OK");
		});
		// The extended menu (MenuTest): a screen for it, made public by the test mod's access widener, and a request
		// to open it once the player has been in the world for a moment. The method is found by its parameters: the game
		// may run with other names than Mojang's (SRG's on Forge).
		try {
			Method register = Arrays.stream(MenuScreens.class.getMethods())
					.filter(m -> Arrays.equals(m.getParameterTypes(), new Class<?>[] {MenuType.class, MenuScreens.ScreenConstructor.class}))
					.findFirst().orElseThrow(() -> new NoSuchMethodException("MenuScreens has no public register(MenuType, ScreenConstructor)"));
			register.invoke(null, MenuTest.TYPE, (MenuScreens.ScreenConstructor<ChestMenu, ContainerScreen>) ContainerScreen::new);
		} catch (ReflectiveOperationException e) {
			TestMod.LOGGER.error("FABRICATED-TEST could not register the test menu's screen", e);
		}
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Once the server has announced it receives the packet (NET-4).
			if (NetworkingTest.OWN && !vanillaPingSent && client.player != null && client.player.tickCount > 40 && ClientPlayNetworking.canSend(NetworkingTest.VanillaPing.TYPE)) {
				vanillaPingSent = true;
				ClientPlayNetworking.send(new NetworkingTest.VanillaPing(11));
			}
			if (!menuRequested && client.player != null && client.player.tickCount > 40 && ClientPlayNetworking.canSend(NetworkingTest.Ping.TYPE)) {
				menuRequested = true;
				ClientPlayNetworking.send(new NetworkingTest.Ping(MenuTest.OPEN_REQUEST));
			}
		});
		WorldStateReport.register();
		ParticleClientTest.register();
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
