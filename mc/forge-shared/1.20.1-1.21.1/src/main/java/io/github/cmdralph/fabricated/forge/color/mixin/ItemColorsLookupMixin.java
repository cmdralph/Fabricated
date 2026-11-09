package io.github.cmdralph.fabricated.forge.color.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.ForgeRegistries;

/** The item colour lookup fabric-rendering-v1's {@code ColorProviderRegistry.ITEM.get} calls, as {@link BlockColorsLookupMixin}'s. */
@Mixin(ItemColors.class)
public abstract class ItemColorsLookupMixin {
	@Shadow
	@Final
	private Map<Holder.Reference<Item>, ItemColor> itemColors;

	public Object get(Object item) {
		return ForgeRegistries.ITEMS.getDelegate(((ItemLike) item).asItem()).map(itemColors::get).orElse(null);
	}
}
