package io.github.cmdralph.fabricated.neoforge.interaction;

import net.fabricmc.fabric.api.event.client.player.ClientHotbarScrollEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Fabric's {@code ClientHotbarScrollEvents} (26.x), fired by Fabricated's mouse handler hook around the hotbar slot
 * change. The scroll amounts the events report come from NeoForge's {@link InputEvent.MouseScrollingEvent}, which
 * the game fires just before (a scroll that event cancels never reaches the hotbar).
 */
public final class ClientHotbarScroll {
	private static double scrollX;
	private static double scrollY;

	private ClientHotbarScroll() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, InputEvent.MouseScrollingEvent.class, event -> {
			scrollX = event.getScrollDeltaX();
			scrollY = event.getScrollDeltaY();
		});
	}

	/** Changes the selected slot as {@code setSlot} would, unless a listener refuses; fires the events around it. */
	public static void scroll(Inventory inventory, int newSlot, Runnable setSlot) {
		int currentSlot = inventory.getSelectedSlot();
		if (!ClientHotbarScrollEvents.ALLOW.invoker().allowScroll(inventory, currentSlot, newSlot, scrollX, scrollY)) return;
		ClientHotbarScrollEvents.BEFORE.invoker().beforeScroll(inventory, currentSlot, newSlot, scrollX, scrollY);
		setSlot.run();
		ClientHotbarScrollEvents.AFTER.invoker().afterScroll(inventory, currentSlot, newSlot, scrollX, scrollY);
	}
}
