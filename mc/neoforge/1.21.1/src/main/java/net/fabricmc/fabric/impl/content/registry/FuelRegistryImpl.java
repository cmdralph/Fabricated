/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.impl.content.registry;

import java.util.LinkedHashMap;
import java.util.Map;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;

// TODO: Clamp values to 32767 (+ add hook for mods which extend the limit to disable the check?)
public final class FuelRegistryImpl implements FuelRegistry {
	private static final Logger LOGGER = LoggerFactory.getLogger(FuelRegistryImpl.class);
	// Fabricated: written under this registry's lock; burn time queries read them from any thread (see burnTime).
	private final Object2IntMap<ItemLike> itemCookTimes = new Object2IntLinkedOpenHashMap<>();
	private final Object2IntMap<TagKey<Item>> tagCookTimes = new Object2IntLinkedOpenHashMap<>();
	private volatile @Nullable Entries entries;

	/** A snapshot of the registrations, items by {@code Item} (Fabricated). */
	private record Entries(Map<Item, Integer> items, Map<TagKey<Item>, Integer> tags) {
	}

	public FuelRegistryImpl() {
	}

	/**
	 * Fabricated: NeoForge's furnaces read burn times from its furnace fuel data map, not from vanilla's table that
	 * upstream adds to, and pass each through {@link FurnaceFuelBurnTimeEvent}. Fabric's registrations apply there first,
	 * over the data map as upstream's over vanilla's table, so NeoForge mods' listeners still see and can change the result.
	 */
	public static void registerBurnTimes() {
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, FurnaceFuelBurnTimeEvent.class,
				event -> ((FuelRegistryImpl) FuelRegistry.INSTANCE).burnTime(event));
	}

	private void burnTime(FurnaceFuelBurnTimeEvent event) {
		Entries entries = entries();
		if (entries.items().isEmpty() && entries.tags().isEmpty()) return;
		ItemStack stack = event.getItemStack();
		// Applied in upstream's order (tags, then items) and as vanilla's table takes them: a removal always, a burn time
		// only for an item that can burn.
		boolean canBurn = !stack.is(ItemTags.NON_FLAMMABLE_WOOD);
		Integer time = null;
		for (Map.Entry<TagKey<Item>, Integer> tag : entries.tags().entrySet()) {
			if (stack.is(tag.getKey())) time = apply(time, tag.getValue(), canBurn);
		}
		Integer itemTime = entries.items().get(stack.getItem());
		if (itemTime != null) time = apply(time, itemTime, canBurn);
		if (time != null) event.setBurnTime(time);
	}

	private static @Nullable Integer apply(@Nullable Integer current, int time, boolean canBurn) {
		if (time <= 0) return 0;
		return canBurn ? Integer.valueOf(time) : current;
	}

	/** Built when first needed after a change, as upstream applies its registrations when vanilla's table is next built. */
	private Entries entries() {
		Entries entries = this.entries;
		if (entries != null) return entries;
		synchronized (this) {
			Map<Item, Integer> items = new LinkedHashMap<>();
			itemCookTimes.forEach((item, time) -> {
				Item asItem = item.asItem();
				if (asItem != Items.AIR) items.put(asItem, time);
			});
			entries = new Entries(items, new LinkedHashMap<>(tagCookTimes));
			this.entries = entries;
			return entries;
		}
	}

	public Map<Item, Integer> getFuelTimes() {
		// Cached by vanilla now
		return AbstractFurnaceBlockEntity.getFuel();
	}

	@Override
	public Integer get(ItemLike item) {
		// Fabricated: the burn time NeoForge's furnaces use (vanilla's table, which upstream reads, is no longer used).
		int time = new ItemStack(item).getBurnTime(null);
		return time > 0 ? time : null;
	}

	@Override
	public void add(ItemLike item, Integer cookTime) {
		if (cookTime > 32767) {
			LOGGER.warn("Tried to register an overly high cookTime: " + cookTime + " > 32767! (" + item + ")");
		}

		synchronized (this) {
			itemCookTimes.put(item, cookTime.intValue());
		}
		resetCache();
	}

	@Override
	public void add(TagKey<Item> tag, Integer cookTime) {
		if (cookTime > 32767) {
			LOGGER.warn("Tried to register an overly high cookTime: " + cookTime + " > 32767! (" + getTagName(tag) + ")");
		}

		synchronized (this) {
			tagCookTimes.put(tag, cookTime.intValue());
		}
		resetCache();
	}

	@Override
	public void remove(ItemLike item) {
		add(item, 0);
		resetCache();
	}

	@Override
	public void remove(TagKey<Item> tag) {
		add(tag, 0);
		resetCache();
	}

	@Override
	public void clear(ItemLike item) {
		synchronized (this) {
			itemCookTimes.removeInt(item);
		}
		resetCache();
	}

	@Override
	public void clear(TagKey<Item> tag) {
		synchronized (this) {
			tagCookTimes.removeInt(tag);
		}
		resetCache();
	}

	public synchronized void apply(Map<Item, Integer> map) {
		// tags take precedence before blocks
		for (TagKey<Item> tag : tagCookTimes.keySet()) {
			int time = tagCookTimes.getInt(tag);

			if (time <= 0) {
				for (Holder<Item> key : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
					final Item item = key.value();
					map.remove(item);
				}
			} else {
				AbstractFurnaceBlockEntity.add(map, tag, time);
			}
		}

		for (ItemLike item : itemCookTimes.keySet()) {
			int time = itemCookTimes.getInt(item);

			if (time <= 0) {
				map.remove(item.asItem());
			} else {
				AbstractFurnaceBlockEntity.add(map, item, time);
			}
		}
	}

	private static String getTagName(TagKey<?> tag) {
		return tag.location().toString();
	}

	public void resetCache() {
		// Fabricated: burn time queries rebuild their snapshot.
		synchronized (this) {
			entries = null;
		}
		// Note: tag reload is already handled by vanilla, see DataPackContents#refresh
		AbstractFurnaceBlockEntity.invalidateCache();
	}
}
