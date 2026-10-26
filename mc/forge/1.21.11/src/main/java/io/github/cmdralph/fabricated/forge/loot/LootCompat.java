package io.github.cmdralph.fabricated.forge.loot;

import net.fabricmc.fabric.impl.loot.FabricLootTable;
import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.event.LootTableLoadEvent;

/** The version-specific part of {@link LootBridge} (1.21.11). */
public final class LootCompat {
	private LootCompat() {
	}

	/** Forge loads the table its loot table event's listeners leave here (SimpleJsonResourceReloadListener's scan). */
	public static void register() {
		LootTableLoadEvent.BUS.addListener(LootBridge::onTableLoaded);
	}

	/** Lets the table's drop hook name the table without searching for it. */
	static void setHolder(LootTable table, Holder<LootTable> holder) {
		((FabricLootTable) (Object) table).fabric$setRegistryEntry(holder);
	}
}
