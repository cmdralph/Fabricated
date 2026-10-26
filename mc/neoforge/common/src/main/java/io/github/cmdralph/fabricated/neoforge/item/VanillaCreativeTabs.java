package io.github.cmdralph.fabricated.neoforge.item;

import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Vanilla's creative tab constructor, which NeoForge's patches gave eight more parameters (its builder's own settings).
 * A Fabric mod's subclass calling it (an access widener opens it: Polymer's tabs for a server's item groups) calls
 * NeoForge's constructor taking a builder instead, with the builder made here from vanilla's arguments (translation's
 * {@code PlatformRenames}, CTOR-1). The other settings keep the builder's defaults, which vanilla's own tabs get.
 */
public final class VanillaCreativeTabs {
	private VanillaCreativeTabs() {
	}

	public static CreativeModeTab.Builder builder(CreativeModeTab.Row row, int column, CreativeModeTab.Type tabType, Component displayName,
			Supplier<ItemStack> iconGenerator, CreativeModeTab.DisplayItemsGenerator displayItemsGenerator) {
		// Its type is set only by a subclass (NeoForge's builder gives a search tab its search bar there).
		return new CreativeModeTab.Builder(row, column) {
			{
				type(tabType);
			}
		}.title(displayName).icon(iconGenerator).displayItems(displayItemsGenerator);
	}
}
