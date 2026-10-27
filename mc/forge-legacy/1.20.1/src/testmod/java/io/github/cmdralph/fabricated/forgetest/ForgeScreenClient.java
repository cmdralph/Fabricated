package io.github.cmdralph.fabricated.forgetest;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.cmdralph.fabricated.forgetest.mixin.MouseHandlerInvoker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.class_310;
import net.minecraft.class_437;

/**
 * The forge-legacy test mod's part for fabric-screen-api-v1's mouse and key events (with Fabric API's jar only): the
 * first time a screen ticks with no overlay up, the client presses and releases a mouse button and a key through the
 * game's own handlers ({@code MouseHandler.onPress}, {@code KeyboardHandler.keyPress}), as GLFW would. The test mod's
 * listeners refuse each, so the screen sees none of them, and all four must be asked. Forge 47's patch made the mouse
 * lambdas take {@code this}, where Mixin 0.8.5 refuses upstream's static handlers, and reordered the key lambda's
 * captured values; pre-flight bridges both (LAMBDA-7). The screen's tick event, which starts it, is upstream's hook into
 * the lambda Forge's {@code Minecraft.tick} creates in vanilla's place (LAMBDA-6).
 */
@Environment(EnvType.CLIENT)
public final class ForgeScreenClient {
	/** GLFW's F24, which nothing binds. */
	private static final int KEY = 313;
	private static final Set<String> ASKED = ConcurrentHashMap.newKeySet();
	/** Whether the test mod is sending its clicks and keys: its listeners refuse only those. */
	private static boolean sending;
	private static boolean sent;

	private ForgeScreenClient() {
	}

	/** From the client entrypoint; only with Fabric API's screen module. */
	static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			ScreenMouseEvents.allowMouseClick(screen).register((shown, x, y, button) -> refuse("click"));
			ScreenMouseEvents.allowMouseRelease(screen).register((shown, x, y, button) -> refuse("release"));
			ScreenKeyboardEvents.allowKeyPress(screen).register((shown, key, scancode, modifiers) -> key != KEY || refuse("key press"));
			ScreenKeyboardEvents.allowKeyRelease(screen).register((shown, key, scancode, modifiers) -> key != KEY || refuse("key release"));
			ScreenEvents.afterTick(screen).register(ForgeScreenClient::send);
		});
	}

	private static boolean refuse(String event) {
		if (!sending) return true;
		ASKED.add(event);
		return false;
	}

	private static void send(class_437 screen) {
		class_310 client = class_310.method_1551();
		if (sent || client.field_1755 != screen || client.method_18506() != null) return;
		sent = true;
		long window = client.method_22683().method_4490();
		sending = true;
		try {
			MouseHandlerInvoker mouse = (MouseHandlerInvoker) client.field_1729;
			mouse.fabricatedtest$onPress(window, 0, 1, 0);
			mouse.fabricatedtest$onPress(window, 0, 0, 0);
			client.field_1774.method_1466(window, KEY, 0, 1, 0);
			client.field_1774.method_1466(window, KEY, 0, 0, 0);
		} finally {
			sending = false;
		}
		if (ASKED.size() == 4) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST screen mouse and key events OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST screen mouse and key events: only {} asked", ASKED);
		}
	}
}
