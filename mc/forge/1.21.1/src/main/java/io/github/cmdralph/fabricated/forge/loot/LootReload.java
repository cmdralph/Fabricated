package io.github.cmdralph.fabricated.forge.loot;

import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.storage.loot.LootTable;

/** What a reload of the reloadable registries gives {@link LootBridge} on 1.21.1: the layered registries themselves. */
final class LootReload {
	private LootReload() {
	}

	/** The loot tables of {@code ReloadableServerRegistries.reload}'s result. */
	@SuppressWarnings("unchecked")
	static Registry<LootTable> lootTables(Object loadResult) {
		return ((LayeredRegistryAccess<RegistryLayer>) loadResult).getLayer(RegistryLayer.RELOADABLE).registryOrThrow(Registries.LOOT_TABLE);
	}
}
