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

package net.fabricmc.fabric.mixin.loot.table;

import java.util.List;

import com.google.common.collect.ImmutableList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import net.fabricmc.fabric.api.loot.v1.FabricLootPool;

/**
 * Fabricated: {@link FabricLootPool#getRolls()} isn't implemented here, as Forge 47's pool has a public
 * {@code getRolls()} of its own, which implements it.
 */
@Mixin(LootPool.class)
public abstract class LootPoolMixin implements FabricLootPool {
	@Shadow
	@Final
	LootPoolEntryContainer[] entries;

	@Shadow
	@Final
	LootItemCondition[] conditions;

	@Shadow
	@Final
	LootItemFunction[] functions;

	@Override
	public List<LootPoolEntryContainer> getEntries() {
		return ImmutableList.copyOf(entries);
	}

	@Override
	public List<LootItemCondition> getConditions() {
		return ImmutableList.copyOf(conditions);
	}

	@Override
	public List<LootItemFunction> getFunctions() {
		return ImmutableList.copyOf(functions);
	}
}
