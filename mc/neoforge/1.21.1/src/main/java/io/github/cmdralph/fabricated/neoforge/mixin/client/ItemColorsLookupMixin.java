package io.github.cmdralph.fabricated.neoforge.mixin.client;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

/**
 * The item color lookup {@code fabric-rendering-v1}'s {@code ColorProviderRegistry.ITEM.get} calls on
 * {@link ItemColors} (its {@code ColorMapperHolder.get}, erased), as {@link BlockColorsLookupMixin} does for blocks:
 * NeoForge 21.1 keeps item colors in a map by item instead of vanilla's id-indexed {@code IdMapper}. Adds a method only.
 */
@Mixin(ItemColors.class)
public abstract class ItemColorsLookupMixin {
	@Shadow
	@Final
	private Map<Item, ItemColor> itemColors;

	public Object get(Object item) {
		return itemColors.get(((ItemLike) item).asItem());
	}
}
