package io.github.cmdralph.fabricated.forge.loot;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.world.level.storage.loot.LootTable;

/** What a reload of the reloadable registries gives {@link LootBridge} on Forge 61 to 66: a {@code LoadResult}. */
final class LootReload {
	private LootReload() {
	}

	/** The loot tables of {@code ReloadableServerRegistries.reload}'s result. */
	static Registry<LootTable> lootTables(Object loadResult) {
		return ((ReloadableServerRegistries.LoadResult) loadResult).layers().getLayer(RegistryLayer.RELOADABLE).lookupOrThrow(Registries.LOOT_TABLE);
	}
}
