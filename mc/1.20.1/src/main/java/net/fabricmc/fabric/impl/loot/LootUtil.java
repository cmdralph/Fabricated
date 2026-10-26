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

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import net.fabricmc.fabric.api.loot.v2.LootTableSource;

/**
 * Fabricated: where a loot table came from, decided on Forge 47. Upstream asks fabric-resource-loader-v0 for the pack's
 * source, which tells mods' packs apart; on Forge each mod's data is a pack of its own, built in as vanilla's is (the
 * {@code isBuiltin} Forge decides which tables its loot event sees by), while a world's data packs aren't. The module
 * doesn't depend on fabric-resource-loader-v0 here, which Fabricated passes through and may be absent.
 */
public final class LootUtil {
	/** Fabricated: vanilla's {@code BuiltInLootTables.EMPTY}, a table data packs can't define. */
	public static final ResourceLocation EMPTY_LOOT_TABLE = new ResourceLocation("empty");

	private LootUtil() {
	}

	public static LootTableSource determineSource(ResourceLocation lootTableId, ResourceManager resourceManager) {
		ResourceLocation resourceId = new ResourceLocation(lootTableId.getNamespace(), "loot_tables/%s.json".formatted(lootTableId.getPath()));
		Optional<Resource> resource = resourceManager.getResource(resourceId);

		if (resource.isPresent()) {
			if ("vanilla".equals(resource.get().sourcePackId())) {
				return LootTableSource.VANILLA;
			} else if (resource.get().isBuiltin()) {
				return LootTableSource.MOD;
			}
		}

		// If not builtin or mod, assume external data pack.
		// It might also be a virtual loot table injected via mixin instead of being loaded
		// from a resource, but we can't determine that here.
		return LootTableSource.DATA_PACK;
	}
}
