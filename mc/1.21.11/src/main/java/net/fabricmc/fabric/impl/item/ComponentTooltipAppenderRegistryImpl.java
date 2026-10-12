package net.fabricmc.fabric.impl.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.component.TooltipProvider;

/**
 * Fabric's tooltip appender registrations (1.21.11). NeoForge 1.21.11 has no way to place a component's tooltip
 * among vanilla's, so Fabricated adds the registered components' tooltips after the item's other tooltip lines, in
 * registration order (first, then before/after, then last); the anchors aren't honoured.
 */
public final class ComponentTooltipAppenderRegistryImpl {
	private static final List<DataComponentType<? extends TooltipProvider>> FIRST = new ArrayList<>();
	private static final List<DataComponentType<? extends TooltipProvider>> MIDDLE = new ArrayList<>();
	private static final List<DataComponentType<? extends TooltipProvider>> LAST = new ArrayList<>();

	private ComponentTooltipAppenderRegistryImpl() {
	}

	public static synchronized void addFirst(DataComponentType<? extends TooltipProvider> componentType) {
		FIRST.add(componentType);
	}

	public static synchronized void addLast(DataComponentType<? extends TooltipProvider> componentType) {
		LAST.add(componentType);
	}

	public static synchronized void addBefore(DataComponentType<?> anchor, DataComponentType<? extends TooltipProvider> componentType) {
		MIDDLE.add(componentType);
	}

	public static synchronized void addAfter(DataComponentType<?> anchor, DataComponentType<? extends TooltipProvider> componentType) {
		MIDDLE.add(componentType);
	}

	public static synchronized List<DataComponentType<? extends TooltipProvider>> inOrder() {
		List<DataComponentType<? extends TooltipProvider>> all = new ArrayList<>(FIRST);
		all.addAll(MIDDLE);
		all.addAll(LAST);
		return all;
	}
}
