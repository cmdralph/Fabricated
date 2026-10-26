package net.fabricmc.fabric.api.loot.v2;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.class_2960;
import net.minecraft.class_3300;
import net.minecraft.class_52;
import net.minecraft.class_60;

public final class LootTableEvents {
	public static final Event<Replace> REPLACE = null;
	public static final Event<Modify> MODIFY = null;
	public static final Event<Loaded> ALL_LOADED = null;

	private LootTableEvents() {
	}

	public interface Replace {
		class_52 replaceLootTable(class_3300 resourceManager, class_60 lootManager, class_2960 id, class_52 original, LootTableSource source);
	}

	public interface Modify {
		void modifyLootTable(class_3300 resourceManager, class_60 lootManager, class_2960 id, class_52.class_53 tableBuilder, LootTableSource source);
	}

	public interface Loaded {
		void onLootTablesLoaded(class_3300 resourceManager, class_60 lootManager);
	}
}
