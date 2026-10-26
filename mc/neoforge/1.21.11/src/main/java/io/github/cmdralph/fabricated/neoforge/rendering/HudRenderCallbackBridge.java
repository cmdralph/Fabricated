package io.github.cmdralph.fabricated.neoforge.rendering;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Fires {@code fabric-rendering-v1}'s (deprecated, still widely used) {@code HudRenderCallback} from NeoForge's
 * {@link RenderGuiEvent.Post}, which NeoForge posts at the end of {@code Gui.render}, where upstream's left-out HUD
 * mixin fires it.
 *
 * <p>The module is loaded like any Fabric mod, so its classes are looked up by name, and nothing is registered when
 * it is not installed.
 */
public final class HudRenderCallbackBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String CALLBACK = "net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback";

	private HudRenderCallbackBridge() {
	}

	public static void register() {
		Class<?> callback;
		try {
			callback = Class.forName(CALLBACK, false, HudRenderCallbackBridge.class.getClassLoader());
		} catch (ClassNotFoundException e) {
			return; // fabric-rendering-v1 is not installed
		}
		Event<?> event;
		MethodHandle onHudRender;
		try {
			event = (Event<?>) callback.getField("EVENT").get(null);
			onHudRender = MethodHandles.publicLookup()
					.findVirtual(callback, "onHudRender", MethodType.methodType(void.class, GuiGraphics.class, DeltaTracker.class))
					.asType(MethodType.methodType(void.class, Object.class, GuiGraphics.class, DeltaTracker.class));
		} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
			LOGGER.error("fabric-rendering-v1's HudRenderCallback has an unexpected shape; it will not fire", e);
			return;
		}
		NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post.class, gui -> {
			try {
				onHudRender.invokeExact((Object) event.invoker(), gui.getGuiGraphics(), gui.getPartialTick());
			} catch (RuntimeException | Error e) {
				throw e;
			} catch (Throwable t) {
				throw new IllegalStateException(t);
			}
		});
	}
}
