package net.fabricmc.fabric.impl.item;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.component.TooltipProvider;
import net.neoforged.neoforge.common.tooltip.TooltipAppender;

/**
 * Fabric's tooltip provider order (26.3), recorded for NeoForge's {@code RegisterTooltipAppendersEvent}, which accepts
 * the same placements (first, last, before or after another component).
 */
public final class ItemComponentTooltipProviderRegistryImpl {
	public enum Position { FIRST, LAST, BEFORE, AFTER }

	public record Entry<T>(Position position, @Nullable DataComponentType<?> anchor, DataComponentType<T> type, TooltipProvider.Getter<T> getter) {
		public TooltipAppender appender() {
			return TooltipAppender.createComponentAppender(type, getter);
		}
	}

	private static final List<Entry<?>> ENTRIES = new ArrayList<>();

	private ItemComponentTooltipProviderRegistryImpl() {
	}

	public static synchronized <T> void addFirst(DataComponentType<T> componentType, TooltipProvider.Getter<T> getter) {
		ENTRIES.add(new Entry<>(Position.FIRST, null, componentType, getter));
	}

	public static synchronized <T> void addLast(DataComponentType<T> componentType, TooltipProvider.Getter<T> getter) {
		ENTRIES.add(new Entry<>(Position.LAST, null, componentType, getter));
	}

	public static synchronized <T> void addBefore(DataComponentType<?> anchor, DataComponentType<T> componentType, TooltipProvider.Getter<T> getter) {
		ENTRIES.add(new Entry<>(Position.BEFORE, anchor, componentType, getter));
	}

	public static synchronized <T> void addAfter(DataComponentType<?> anchor, DataComponentType<T> componentType, TooltipProvider.Getter<T> getter) {
		ENTRIES.add(new Entry<>(Position.AFTER, anchor, componentType, getter));
	}

	public static synchronized List<Entry<?>> entries() {
		return List.copyOf(ENTRIES);
	}
}
