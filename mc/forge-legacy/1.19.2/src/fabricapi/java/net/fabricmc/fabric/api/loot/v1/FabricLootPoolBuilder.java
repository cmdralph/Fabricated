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

package net.fabricmc.fabric.api.loot.v1;

import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;

/**
 * @deprecated Replaced with {@link net.fabricmc.fabric.api.loot.v2.FabricLootPoolBuilder}.
 */
@Deprecated
public class FabricLootPoolBuilder extends LootPool.Builder {
	private FabricLootPoolBuilder() { }

	private FabricLootPoolBuilder(LootPool pool) {
		copyFrom(pool, true);
	}

	private net.fabricmc.fabric.api.loot.v2.FabricLootPoolBuilder asV2() {
		return (net.fabricmc.fabric.api.loot.v2.FabricLootPoolBuilder) this;
	}

	// Fabricated: these override the vanilla builder's methods, under Mojang's names (Yarn's rolls, with and conditionally).
	@Override
	public FabricLootPoolBuilder setRolls(NumberProvider range) {
		super.setRolls(range);
		return this;
	}

	@Override
	public FabricLootPoolBuilder add(LootPoolEntryContainer.Builder<?> entry) {
		super.add(entry);
		return this;
	}

	@Override
	public FabricLootPoolBuilder when(LootItemCondition.Builder condition) {
		super.when(condition);
		return this;
	}

	@Override
	public FabricLootPoolBuilder apply(LootItemFunction.Builder function) {
		super.apply(function);
		return this;
	}

	// Fabricated: the overrides above under upstream's names (Yarn's rolls, with and conditionally), with upstream's return
	// type: Fabric mods call them by those names, which an override with another return type keeps in Intermediary's
	// names (only its bridges take Intermediary's), and Fabricated leaves them as they are.
	public FabricLootPoolBuilder rolls(NumberProvider range) {
		return setRolls(range);
	}

	public FabricLootPoolBuilder with(LootPoolEntryContainer.Builder<?> entry) {
		return add(entry);
	}

	public FabricLootPoolBuilder conditionally(LootItemCondition.Builder condition) {
		return when(condition);
	}

	public FabricLootPoolBuilder withEntry(LootPoolEntryContainer entry) {
		asV2().with(entry);
		return this;
	}

	public FabricLootPoolBuilder withCondition(LootItemCondition condition) {
		asV2().conditionally(condition);
		return this;
	}

	public FabricLootPoolBuilder withFunction(LootItemFunction function) {
		asV2().apply(function);
		return this;
	}

	/**
	 * Copies the entries, conditions and functions of the {@code pool} to this
	 * builder.
	 *
	 * <p>This is equal to {@code copyFrom(pool, false)}.
	 */
	public FabricLootPoolBuilder copyFrom(LootPool pool) {
		return copyFrom(pool, false);
	}

	/**
	 * Copies the entries, conditions and functions of the {@code pool} to this
	 * builder.
	 *
	 * <p>If {@code copyRolls} is true, the {@link FabricLootPool#getRolls rolls} of the pool are also copied.
	 */
	public FabricLootPoolBuilder copyFrom(LootPool pool, boolean copyRolls) {
		FabricLootPool extended = (FabricLootPool) pool;
		asV2().with(extended.getEntries());
		asV2().conditionally(extended.getConditions());
		asV2().apply(extended.getFunctions());

		if (copyRolls) {
			setRolls(extended.getRolls());
		}

		return this;
	}

	public static FabricLootPoolBuilder builder() {
		return new FabricLootPoolBuilder();
	}

	public static FabricLootPoolBuilder of(LootPool pool) {
		return new FabricLootPoolBuilder(pool);
	}
}
