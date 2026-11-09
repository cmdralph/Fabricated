package io.github.cmdralph.fabricated.forge.loot;

import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** The version-specific part of {@link LootBridge} (1.21.1, Forge 52). */
public final class LootCompat {
	private LootCompat() {
	}

	/**
	 * Forge loads the table its loot table event's listeners leave (its {@code LootDataType.deserialize}, for each table
	 * {@code ReloadableServerRegistries} reads).
	 */
	public static void register() {
		MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LootTableLoadEvent.class, LootBridge::onTableLoaded);
	}

	/** Nothing to name: Fabric API 0.116's loot module has no drop hook, which later ones name a table for. */
	static void setHolder(LootTable table, Holder<LootTable> holder) {
	}
}
