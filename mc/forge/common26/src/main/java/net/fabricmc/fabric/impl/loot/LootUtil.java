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

package net.fabricmc.fabric.impl.loot;

import java.util.function.Function;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.storage.loot.LootTable;

import net.fabricmc.fabric.api.loot.v3.LootTableSource;

public final class LootUtil {
	// Fabricated: on Forge, from the id of the pack the file comes from, as fabric-resource-loader-v1 (passed through,
	// or not installed) doesn't tell the in-game mod its pack sources: vanilla's built-in data pack, Forge's packs of mod
	// files ("mod:<id>", and their merged "mod_resources"), and any other pack (a world's data packs, vanilla's feature
	// packs) as a data pack, as upstream classifies them.
	public static LootTableSource determineSource(Resource resource) {
		if (resource != null) {
			String pack = resource.sourcePackId();

			if (pack.equals("vanilla")) {
				return LootTableSource.VANILLA;
			} else if (pack.startsWith("mod:") || pack.equals("mod_resources")) {
				return LootTableSource.MOD;
			}
		}

		// If not builtin or mod, assume external data pack.
		// It might also be a virtual loot table injected via mixin instead of being loaded
		// from a resource, but we can't determine that here.
		return LootTableSource.DATA_PACK;
	}

	public static Holder<LootTable> getEntryOrDirect(ServerLevel level, LootTable table) {
		HolderLookup.Provider provider = level
				.getServer()
				.reloadableRegistries()
				.lookup();

		HolderLookup<LootTable> lootTableHolderLookup = provider
				.lookup(Registries.LOOT_TABLE)
				.orElseThrow(() -> new IllegalStateException("Failed to fetch LootTable provider from HolderLookup.Provider"));

		return lootTableHolderLookup
				.listElements()
				.filter(it -> it.value().equals(table))
				.findFirst()
				.map(Function.<Holder<LootTable>>identity())
				.orElseGet(() -> Holder.direct(table));
	}
}
