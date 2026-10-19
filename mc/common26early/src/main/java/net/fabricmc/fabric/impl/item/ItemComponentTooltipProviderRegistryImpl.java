package net.fabricmc.fabric.impl.item;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.component.TooltipProvider;
import net.neoforged.neoforge.common.tooltip.TooltipAppender;

/**
 * Fabric's tooltip provider order, recorded for NeoForge's {@code RegisterTooltipAppendersEvent}, which accepts the
 * same placements (first, last, before or after another component).
 */
public final class ItemComponentTooltipProviderRegistryImpl {
	public enum Position { FIRST, LAST, BEFORE, AFTER }

	public record Entry(Position position, @Nullable DataComponentType<?> anchor, DataComponentType<? extends TooltipProvider> type) {
		public TooltipAppender appender() {
			return TooltipAppender.createComponentAppender(type);
		}
	}

	private static final List<Entry> ENTRIES = new ArrayList<>();

	private ItemComponentTooltipProviderRegistryImpl() {
	}

	public static synchronized void addFirst(DataComponentType<? extends TooltipProvider> componentType) {
		ENTRIES.add(new Entry(Position.FIRST, null, componentType));
	}

	public static synchronized void addLast(DataComponentType<? extends TooltipProvider> componentType) {
		ENTRIES.add(new Entry(Position.LAST, null, componentType));
	}

	public static synchronized void addBefore(DataComponentType<?> anchor, DataComponentType<? extends TooltipProvider> componentType) {
		ENTRIES.add(new Entry(Position.BEFORE, anchor, componentType));
	}

	public static synchronized void addAfter(DataComponentType<?> anchor, DataComponentType<? extends TooltipProvider> componentType) {
		ENTRIES.add(new Entry(Position.AFTER, anchor, componentType));
	}

	public static synchronized List<Entry> entries() {
		return List.copyOf(ENTRIES);
	}
}
