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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;

import net.fabricmc.fabric.api.loot.v2.FabricLootTableBuilder;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.fabric.api.loot.v2.LootTableSource;
import net.fabricmc.fabric.impl.loot.LootUtil;

/**
 * Implements the events from {@link LootTableEvents}.
 *
 * <p>Fabricated: upstream's {@code LootManagerMixin}, on Forge 47. Upstream replaces the loaded tables once the reload
 * has finished, in the map vanilla keeps them in; here the tables are replaced as vanilla receives them, in the parsed
 * tables {@code apply} is handed, which Forge's loot event has already seen: vanilla then checks the replaced tables and
 * keeps them, and nothing names the map vanilla keeps. A rebuilt table gets back what Forge gave the parsed one, its id
 * (which Forge's global loot modifiers look tables up by) and its freeze. The handlers are named apart from the game's
 * methods, as the in-game mod is reobfuscated.
 */
@Mixin(LootDataManager.class)
abstract class LootDataManagerMixin {
	/** The resource manager of the reload {@code apply} ends, kept from the reload's start until then. */
	@Unique
	@Nullable
	private ResourceManager fabric_resourceManager;

	@Inject(method = "reload", at = @At("HEAD"))
	private void fabric_keepResourceManager(PreparableReloadListener.PreparationBarrier barrier, ResourceManager manager, ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler, Executor prepareExecutor, Executor applyExecutor, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
		fabric_resourceManager = manager;
	}

	@SuppressWarnings("unchecked")
	@Inject(method = "apply", at = @At("HEAD"))
	private void fabric_applyLootTableEvents(Map<LootDataType<?>, Map<ResourceLocation, ?>> parsed, CallbackInfo ci) {
		ResourceManager resourceManager = fabric_resourceManager;
		// The parsed tables, by id: a map of its own for each type, which vanilla fills as it parses them.
		Map<ResourceLocation, Object> tables = (Map<ResourceLocation, Object>) parsed.get(LootDataType.TABLE);
		if (resourceManager == null || tables == null) return;
		//noinspection DataFlowIssue
		LootDataManager lootManager = (LootDataManager) (Object) this;

		tables.replaceAll((id, entry) -> {
			if (LootUtil.EMPTY_LOOT_TABLE.equals(id)) {
				// This is a special table and cannot be modified.
				// Vanilla also warns about that (and leaves out a data pack's, just after this).
				return entry;
			}

			if (!(entry instanceof LootTable table)) {
				// We only want to modify loot tables
				return entry;
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

			// Turn the builder back into a loot table, which Forge names and freezes as it loads one.
			LootTable rebuilt = builder.build();
			rebuilt.setLootTableId(id);
			rebuilt.freeze();
			return rebuilt;
		});
	}

	@Inject(method = "apply", at = @At("RETURN"))
	private void fabric_onLootTablesLoaded(Map<LootDataType<?>, Map<ResourceLocation, ?>> parsed, CallbackInfo ci) {
		ResourceManager resourceManager = fabric_resourceManager;
		fabric_resourceManager = null;
		//noinspection DataFlowIssue
		if (resourceManager != null) LootTableEvents.ALL_LOADED.invoker().onLootTablesLoaded(resourceManager, (LootDataManager) (Object) this);
	}
}
