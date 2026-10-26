package io.github.cmdralph.fabricated.forge.loot;

import java.util.Optional;

import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.fabricmc.fabric.impl.loot.FabricLootTable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The version-specific part of {@link LootBridge} (26.3). Forge 66 loads loot tables as registry elements, decoding each
 * as an {@link Optional} (empty when its conditions aren't met), and fires its loot table event without using the table
 * the listeners leave: Fabric's events run from Fabricated's hook on that decoding instead
 * ({@code ResourceManagerRegistryLoadTaskMixin}).
 */
public final class LootCompat {
	private LootCompat() {
	}

	/** Nothing to register: Forge 66 drops what its loot table event's listeners leave. */
	public static void register() {
	}

	static void setHolder(LootTable table, Holder<LootTable> holder) {
		((FabricLootTable) (Object) table).fabric$setHolder(holder);
	}

	/** A decoded element of the loot table registry, as Forge 66 decodes it or as vanilla does, with Fabric's events run. */
	public static Object modify(Object value, ResourceKey<LootTable> key, LootTableSource source, HolderLookup.Provider registries) {
		if (value instanceof LootTable table) return LootBridge.modify(key, table, source, registries);
		if (value instanceof Optional<?> optional) {
			return optional.map(element -> element instanceof LootTable table ? LootBridge.modify(key, table, source, registries) : element);
		}
		return value;
	}
}
