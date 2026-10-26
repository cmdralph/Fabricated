package io.github.cmdralph.fabricated.neoforge.loot;

import net.fabricmc.fabric.impl.loot.FabricLootTable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/** The version-specific part of {@link LootBridge} (1.21.11). */
public final class LootCompat {
	private LootCompat() {
	}

	/** NeoForge's event already offers the lookup Fabric's events take. */
	static HolderLookup.Provider registries(LootTableLoadEvent event) {
		return event.getRegistries();
	}

	/** Lets the table's drop hook name the table without searching for it. */
	static void setHolder(LootTable table, Holder<LootTable> holder) {
		((FabricLootTable) (Object) table).fabric$setRegistryEntry(holder);
	}

	/** The loot table registry of a finished reload ({@code ReloadableServerRegistries.LoadResult}). */
	public static Registry<LootTable> lootTables(Object loadResult) {
		return ((ReloadableServerRegistries.LoadResult) loadResult).layers().getLayer(RegistryLayer.RELOADABLE).lookupOrThrow(Registries.LOOT_TABLE);
	}
}
