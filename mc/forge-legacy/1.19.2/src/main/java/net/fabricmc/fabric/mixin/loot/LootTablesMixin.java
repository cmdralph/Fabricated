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

package net.fabricmc.fabric.mixin.loot;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootTables;

import net.fabricmc.fabric.api.loot.v2.FabricLootTableBuilder;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.fabric.api.loot.v2.LootTableSource;
import net.fabricmc.fabric.impl.loot.LootUtil;

/**
 * Implements the events from {@link LootTableEvents}.
 *
 * <p>Fabricated: upstream's {@code LootManagerMixin}, on Forge 43. Upstream replaces the tables once {@code apply} has
 * returned, in the map vanilla keeps them in; here the tables are replaced as {@code apply} builds that map, once
 * Forge's loot event has seen each parsed table: vanilla then checks the replaced tables and keeps them, as on Forge 47.
 * A rebuilt table gets back what Forge gave the parsed one, its id (which Forge's global loot modifiers look tables up
 * by; unless a Fabric mod gave it that id as it was built) and its freeze. The handler is named apart from the game's
 * methods, as the in-game mod is reobfuscated.
 */
@Mixin(LootTables.class)
abstract class LootTablesMixin {
	@ModifyExpressionValue(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
			at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableMap$Builder;build()Lcom/google/common/collect/ImmutableMap;"))
	private ImmutableMap<ResourceLocation, LootTable> fabric_applyLootTableEvents(ImmutableMap<ResourceLocation, LootTable> tables,
			Map<ResourceLocation, JsonElement> jsonMap, ResourceManager resourceManager, ProfilerFiller profiler) {
		// The builder for the new map with modified loot tables.
		// We're using an immutable map to match vanilla.
		ImmutableMap.Builder<ResourceLocation, LootTable> newTables = ImmutableMap.builder();
		//noinspection DataFlowIssue
		LootTables lootManager = (LootTables) (Object) this;

		tables.forEach((id, table) -> {
			if (id.equals(BuiltInLootTables.EMPTY)) {
				// This is a special table and cannot be modified.
				// Vanilla also warns about that (and left out a data pack's).
				newTables.put(id, table);
				return;
			}

			LootTableSource source = LootUtil.determineSource(id, resourceManager);
			// Invoke the REPLACE event for the current loot table.
			LootTable replacement = LootTableEvents.REPLACE.invoker().replaceLootTable(resourceManager, lootManager, id, table, source);

			if (replacement != null) {
				// Set the loot table to MODIFY to be the replacement loot table.
				// The MODIFY event will also see it as a replaced loot table via the source.
				table = replacement;
				source = LootTableSource.REPLACED;
			}

			// Turn the current table into a modifiable builder and invoke the MODIFY event.
			LootTable.Builder builder = FabricLootTableBuilder.copyOf(table);
			LootTableEvents.MODIFY.invoker().modifyLootTable(resourceManager, lootManager, id, builder, source);

			// Turn the builder back into a loot table, which Forge names and freezes as it loads one. A Fabric mod may
			// have named it as it was built, and Forge refuses to name a table twice, even by the same name (CORPUS-90).
			LootTable rebuilt = builder.build();
			if (!id.equals(rebuilt.getLootTableId())) rebuilt.setLootTableId(id);
			rebuilt.freeze();
			newTables.put(id, rebuilt);
		});

		return newTables.build();
	}
}
