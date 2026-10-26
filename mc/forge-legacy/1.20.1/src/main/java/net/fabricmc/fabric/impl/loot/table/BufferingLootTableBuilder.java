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

package net.fabricmc.fabric.impl.loot.table;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;

import net.fabricmc.fabric.api.loot.v1.FabricLootSupplier;
import net.fabricmc.fabric.api.loot.v1.FabricLootSupplierBuilder;
import net.fabricmc.fabric.api.loot.v2.FabricLootTableBuilder;

/**
 * A {@link FabricLootSupplierBuilder} that caches all methods so they can be applied to a v2 {@link FabricLootTableBuilder}.
 * Used for hooking {@code LootTableLoadingCallback} to the two different {@link net.fabricmc.fabric.api.loot.v2.LootTableEvents}.
 */
public class BufferingLootTableBuilder extends FabricLootSupplierBuilder {
	private final List<Consumer<LootTable.Builder>> modifications = new ArrayList<>();

	private FabricLootSupplierBuilder addAction(Consumer<LootTable.Builder> action) {
		modifications.add(action);
		return this;
	}

	private FabricLootSupplierBuilder addV2Action(Consumer<FabricLootTableBuilder> action) {
		return addAction(builder -> action.accept((FabricLootTableBuilder) builder));
	}

	// Fabricated: these override the vanilla builder's methods, under Mojang's names (Yarn's pool and type).
	@Override
	public FabricLootSupplierBuilder withPool(LootPool.Builder pool) {
		super.withPool(pool);
		return addAction(builder -> builder.withPool(pool));
	}

	@Override
	public FabricLootSupplierBuilder setParamSet(LootContextParamSet type) {
		super.setParamSet(type);
		return addAction(builder -> builder.setParamSet(type));
	}

	@Override
	public FabricLootSupplierBuilder apply(LootItemFunction.Builder function) {
		super.apply(function);
		return addAction(builder -> builder.apply(function));
	}

	@Override
	public FabricLootSupplierBuilder withPool(LootPool pool) {
		super.withPool(pool);
		return addV2Action(builder -> builder.pool(pool));
	}

	@Override
	public FabricLootSupplierBuilder withFunction(LootItemFunction function) {
		super.withFunction(function);
		return addV2Action(builder -> builder.apply(function));
	}

	@Override
	public FabricLootSupplierBuilder withPools(Collection<LootPool> pools) {
		super.withPools(pools);
		return addV2Action(builder -> builder.pools(pools));
	}

	@Override
	public FabricLootSupplierBuilder withFunctions(Collection<LootItemFunction> functions) {
		super.withFunctions(functions);
		return addV2Action(builder -> builder.apply(functions));
	}

	@Override
	public FabricLootSupplierBuilder copyFrom(LootTable supplier, boolean copyType) {
		super.copyFrom(supplier, copyType);
		return addV2Action(builder -> {
			FabricLootSupplier extended = (FabricLootSupplier) supplier;
			builder.pools(extended.getPools());
			builder.apply(extended.getFunctions());

			if (copyType) {
				((LootTable.Builder) builder).setParamSet(supplier.getParamSet());
			}
		});
	}

	public void init(LootTable original) {
		super.setParamSet(original.getParamSet());
		super.withPools(((FabricLootSupplier) original).getPools());
		super.withFunctions(((FabricLootSupplier) original).getFunctions());
	}

	public void applyTo(LootTable.Builder builder) {
		for (Consumer<LootTable.Builder> modification : modifications) {
			modification.accept(builder);
		}
	}
}
