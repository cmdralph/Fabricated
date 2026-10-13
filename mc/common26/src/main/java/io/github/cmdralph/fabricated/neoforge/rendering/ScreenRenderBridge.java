package io.github.cmdralph.fabricated.neoforge.rendering;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Fires {@code fabric-screen-api-v1}'s {@code ScreenEvents.beforeExtract}/{@code afterExtract} from NeoForge's
 * {@link ScreenEvent.Render} events, which NeoForge posts around the same call upstream's mixin wraps (that mixin
 * handler is left out by the Fabric API plan; the rest of the module is upstream's code).
 *
 * <p>The module is loaded like any Fabric mod, so its classes are looked up by name, and nothing is registered when
 * it is not installed.
 */
public final class ScreenRenderBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String EVENTS = "net.fabricmc.fabric.api.client.screen.v1.ScreenEvents";

	private final MethodHandle beforeEvent;
	private final MethodHandle before;
	private final MethodHandle afterEvent;
	private final MethodHandle after;

	private ScreenRenderBridge(MethodHandle beforeEvent, MethodHandle before, MethodHandle afterEvent, MethodHandle after) {
		this.beforeEvent = beforeEvent;
		this.before = before;
		this.afterEvent = afterEvent;
		this.after = after;
	}

	public static void register() {
		ClassLoader loader = ScreenRenderBridge.class.getClassLoader();
		Class<?> events;
		try {
			events = Class.forName(EVENTS, false, loader);
		} catch (ClassNotFoundException e) {
			return; // fabric-screen-api-v1 is not installed
		}
		ScreenRenderBridge bridge;
		try {
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			MethodType call = MethodType.methodType(void.class, Screen.class, GuiGraphicsExtractor.class, int.class, int.class, float.class);
			MethodType erased = call.insertParameterTypes(0, Object.class);
			bridge = new ScreenRenderBridge(
					lookup.findStatic(events, "beforeExtract", MethodType.methodType(Event.class, Screen.class)),
					lookup.findVirtual(Class.forName(EVENTS + "$BeforeExtract", false, loader), "beforeExtract", call).asType(erased),
					lookup.findStatic(events, "afterExtract", MethodType.methodType(Event.class, Screen.class)),
					lookup.findVirtual(Class.forName(EVENTS + "$AfterExtract", false, loader), "afterExtract", call).asType(erased));
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.error("fabric-screen-api-v1's ScreenEvents has an unexpected shape; its extract events will not fire", e);
			return;
		}
		// Fabric's events run right around the screen: after NeoForge mods' Pre listeners (and only when none cancelled
		// the rendering), before their Post listeners.
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ScreenEvent.Render.Pre.class,
				event -> bridge.fire(bridge.beforeEvent, bridge.before, event));
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ScreenEvent.Render.Post.class,
				event -> bridge.fire(bridge.afterEvent, bridge.after, event));
	}

	private void fire(MethodHandle eventOf, MethodHandle call, ScreenEvent.Render event) {
		Screen screen = event.getScreen();
		try {
			Event<?> fabricEvent = (Event<?>) eventOf.invokeExact(screen);
			call.invokeExact((Object) fabricEvent.invoker(), screen, event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}
}
