package io.github.cmdralph.fabricated.neoforge.loot;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/** The version-specific part of {@link LootBridge} (1.21.1). */
public final class LootCompat {
	private LootCompat() {
	}

	/** NeoForge's event already offers the lookup Fabric's events take. */
	static HolderLookup.Provider registries(LootTableLoadEvent event) {
		return event.getRegistries();
	}

	/** Fabric API 0.116 has no drop hook that needs the table's holder. */
	static void setHolder(LootTable table, Holder<LootTable> holder) {
	}

	/** The loot table registry of a finished reload (the reload's {@code LayeredRegistryAccess}). */
	@SuppressWarnings("unchecked")
	public static Registry<LootTable> lootTables(Object loadResult) {
		return ((LayeredRegistryAccess<RegistryLayer>) loadResult).getLayer(RegistryLayer.RELOADABLE).registryOrThrow(Registries.LOOT_TABLE);
	}
}
