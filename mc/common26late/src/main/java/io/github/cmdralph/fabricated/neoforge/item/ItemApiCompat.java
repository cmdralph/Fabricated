package io.github.cmdralph.fabricated.neoforge.item;

import net.fabricmc.fabric.api.item.v1.ItemClickBehaviorCallback;
import net.fabricmc.fabric.api.util.EventResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;

/** The version-specific part of {@link ItemApiBridge} (26.2+): Fabric's item click behaviour. */
final class ItemApiCompat {
	private ItemApiCompat() {
	}

	static void register() {
		NeoForge.EVENT_BUS.addListener(ItemStackedOnOtherEvent.class, ItemApiCompat::onItemClick);
	}

	/**
	 * Fabric's {@code ItemClickBehaviorCallback}, from NeoForge's event for the same click: a result other than
	 * {@code PASS} decides the click, as upstream (the item's own click overrides then don't run).
	 */
	private static void onItemClick(ItemStackedOnOtherEvent event) {
		EventResult result = ItemClickBehaviorCallback.EVENT.invoker().onItemClickBehavior(event.getStackedOnItem(), event.getSlot(),
				event.getCarriedItem(), event.getCarriedSlotAccess(), event.getClickAction(), event.getPlayer());
		if (result != EventResult.PASS) {
			event.setCanceled(true);
			event.setCancellationResult(!result.allowAction());
		}
	}
}
