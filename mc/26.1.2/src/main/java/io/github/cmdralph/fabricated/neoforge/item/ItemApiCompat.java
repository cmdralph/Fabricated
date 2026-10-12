package io.github.cmdralph.fabricated.neoforge.item;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.impl.item.ItemComponentTooltipProviderRegistryImpl;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.component.TooltipProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.tooltip.TooltipAppender;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;

/** Native {@code fabric-item-api-v1}, the parts that differ on 26.1.2: tooltip provider order. */
public final class ItemApiCompat {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	private ItemApiCompat() {
	}

	static void register(IEventBus modBus) {
		modBus.addListener(RegisterTooltipAppendersEvent.class, ItemApiCompat::registerTooltipProviders);
	}

	/** Fabric's {@code ItemComponentTooltipProviderRegistry}, placed where NeoForge places component tooltips. */
	private static void registerTooltipProviders(RegisterTooltipAppendersEvent event) {
		for (var entry : ItemComponentTooltipProviderRegistryImpl.entries()) {
			TooltipAppender appender = appender(entry.type());
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

	private static <T extends TooltipProvider> TooltipAppender appender(DataComponentType<T> type) {
		return TooltipAppender.createComponentAppender(type);
	}
}
