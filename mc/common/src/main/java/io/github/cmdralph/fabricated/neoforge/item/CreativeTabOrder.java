package io.github.cmdralph.fabricated.neoforge.item;

import java.lang.reflect.Field;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;

/**
 * NeoForge works out where each creative tab goes once, as its registration events end (a tab that asks for no place
 * goes after the vanilla tabs), and orders the tabs by that on each resource reload: a tab registered later has no
 * place and lands among the others by name. Tabs Fabric mods register later (on a client, main initializers run in
 * {@code Minecraft}'s constructor, CORPUS-16) get their places from NeoForge's sort, run again.
 */
public final class CreativeTabOrder {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");

	private CreativeTabOrder() {
	}

	public static void sortLate() {
		List<CreativeModeTab> sorted = CreativeModeTabRegistry.getSortedCreativeModeTabs();
		List<CreativeModeTab> defaults = CreativeModeTabRegistry.getDefaultTabs();
		if (BuiltInRegistries.CREATIVE_MODE_TAB.stream().allMatch(tab -> sorted.contains(tab) || defaults.contains(tab))) return;
		try {
			// NeoForge's sort adds the default tabs (search, inventory...) to this list each time it runs.
			Field field = CreativeModeTabRegistry.class.getDeclaredField("DEFAULT_TABS");
			field.setAccessible(true);
			((List<?>) field.get(null)).clear();
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Creative tabs registered by Fabric mods after NeoForge ordered the tabs are shown among the others "
					+ "by name: NeoForge's default tab list can't be reset", e);
			return;
		}
		CreativeModeTabRegistry.sortTabs();
	}
}
