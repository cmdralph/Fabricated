package io.github.cmdralph.fabricated.neoforge.item;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.item.v1.ItemClickBehaviorCallback;
import net.fabricmc.fabric.api.util.EventResult;
import net.fabricmc.fabric.impl.item.ItemComponentTooltipProviderRegistryImpl;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.tooltip.TooltipAppender;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;

/** Native {@code fabric-item-api-v1}, the parts that differ on 26.3: tooltip provider order and item click behaviour. */
public final class ItemApiCompat {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	private ItemApiCompat() {
	}

	static void register(IEventBus modBus) {
		modBus.addListener(RegisterTooltipAppendersEvent.class, ItemApiCompat::registerTooltipProviders);
		NeoForge.EVENT_BUS.addListener(ItemStackedOnOtherEvent.class, ItemApiCompat::onItemClick);
	}

	/** Fabric's {@code ItemComponentTooltipProviderRegistry}, placed where NeoForge places component tooltips. */
	private static void registerTooltipProviders(RegisterTooltipAppendersEvent event) {
		for (var entry : ItemComponentTooltipProviderRegistryImpl.entries()) {
			TooltipAppender appender = entry.appender();
			try {
				switch (entry.position()) {
					case FIRST -> event.registerComponentAppenderBeforeAll(entry.type(), appender);
					case LAST -> event.registerComponentAppenderAfterAll(entry.type(), appender);
					case BEFORE -> event.registerComponentAppenderBefore(entry.type(), entry.anchor(), appender);
					case AFTER -> event.registerComponentAppenderAfter(entry.type(), entry.anchor(), appender);
				}
			} catch (IllegalStateException e) {
				LOGGER.warn("Tooltip provider for component {} is registered more than once; the first registration is kept", entry.type());
			}
		}
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
