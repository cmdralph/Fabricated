package io.github.cmdralph.fabricated.forge.color.mixin;

import java.util.HashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.IdMappingEvent;

/**
 * The item colours' map of colour providers by item id, as {@link BlockColorsIdMapperMixin} restores the block colours'
 * (COLOR-1): Fabric API's colour provider registry reads an item's provider from it.
 */
@Mixin(value = ItemColors.class, priority = 100)
abstract class ItemColorsIdMapperMixin {
	private volatile IdMapper<ItemColor> f_92674_ = new IdMapper<>(32);
	@Unique
	private final Map<Item, ItemColor> fabricated$providers = new HashMap<>();

	@Inject(method = "<init>", at = @At("RETURN"))
	private void fabricated$followIdChanges(CallbackInfo ci) {
		MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, IdMappingEvent.class, event -> fabricated$mapByIds());
	}

	@Inject(method = "register", at = @At("TAIL"))
	private void fabricated$mapById(ItemColor provider, ItemLike[] items, CallbackInfo ci) {
		for (ItemLike item : items) {
			fabricated$providers.put(item.asItem(), provider);
			f_92674_.addMapping(provider, BuiltInRegistries.ITEM.getId(item.asItem()));
		}
	}

	@Unique
	private void fabricated$mapByIds() {
		IdMapper<ItemColor> ids = new IdMapper<>(32);
		fabricated$providers.forEach((item, provider) -> ids.addMapping(provider, BuiltInRegistries.ITEM.getId(item)));
		f_92674_ = ids;
	}
}
