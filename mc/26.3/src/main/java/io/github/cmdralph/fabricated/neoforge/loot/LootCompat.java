package io.github.cmdralph.fabricated.neoforge.loot;

import java.util.Optional;
import java.util.stream.Stream;

import net.fabricmc.fabric.impl.loot.FabricLootTable;
import net.fabricmc.fabric.impl.loot.LootTableLookup;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/** The version-specific part of {@link LootBridge} (26.3). */
public final class LootCompat {
	private LootCompat() {
	}

	/**
	 * NeoForge's event offers the loading context as a {@link HolderGetter.Provider}; Fabric's events take a
	 * {@link HolderLookup.Provider}. Getting entries and tags works through it (loot tables included, as upstream);
	 * listing a registry's entries or tags gives nothing.
	 */
	static HolderLookup.Provider registries(LootTableLoadEvent event) {
		return new GetterProvider(event.getRegistries());
	}

	static void setHolder(LootTable table, Holder<LootTable> holder) {
		((FabricLootTable) (Object) table).fabric$setHolder(holder);
	}

	public static Registry<LootTable> lootTables(Object loadResult) {
		return ((ReloadableServerRegistries.LoadResult) loadResult).layers().getLayer(RegistryLayer.RELOADABLE).lookupOrThrow(Registries.LOOT_TABLE);
	}

	private record GetterProvider(HolderGetter.Provider getters) implements HolderLookup.Provider {
		@Override
		public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
			return Stream.empty();
		}

		@Override
		public <T> Optional<? extends HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
			return this.getters.lookup(key).map(getter -> new LootTableLookup<T>(key, getter, null));
		}
	}
}
