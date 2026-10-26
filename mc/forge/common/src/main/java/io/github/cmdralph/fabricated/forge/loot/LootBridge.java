package io.github.cmdralph.fabricated.forge.loot;

import java.lang.ref.WeakReference;

import net.fabricmc.fabric.api.loot.v3.FabricLootTableBuilder;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.fabricmc.fabric.impl.loot.LootUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.event.LootTableLoadEvent;

/**
 * Native {@code fabric-loot-api-v3} on Forge (F4), as {@code main}'s on NeoForge. Upstream's builder, accessor and drop
 * mixins apply on Forge as they are and are shipped unchanged; its loading hooks are replaced:
 *
 * <ul>
 * <li>{@code REPLACE} and {@code MODIFY} run once per loot table, for one table at a time (mods' handlers aren't always
 * safe to run at once): from Forge's {@link LootTableLoadEvent} on Forge 61 to 65, which load loot tables through
 * {@code SimpleJsonResourceReloadListener} and use the table the event's listeners leave; from Fabricated's hook into the
 * registry loading on Forge 66, which loads them as registry elements and drops what those listeners leave
 * ({@link LootCompat});</li>
 * <li>{@code ALL_LOADED} runs once the reload's loot table registry is complete, from Fabricated's hook on
 * {@code ReloadableServerRegistries.reload}, which also notes the resource manager and the registries the reload loads
 * with.</li>
 * </ul>
 */
public final class LootBridge {
	private static final FileToIdConverter LOOT_TABLES = FileToIdConverter.registry(Registries.LOOT_TABLE);
	private static volatile WeakReference<ResourceManager> reloadingManager = new WeakReference<>(null);
	private static volatile HolderLookup.Provider loadingRegistries;
	/** Held while mods' handlers modify a table. */
	private static final Object HANDLERS = new Object();

	private LootBridge() {
	}

	/** From Fabricated's hook: a reload of the server's loot tables (and other reloadable registries) starts. */
	public static void startReload(ResourceManager manager) {
		reloadingManager = new WeakReference<>(manager);
	}

	/** The resource manager of the reload that started last, or none. */
	public static ResourceManager reloadingManager() {
		return reloadingManager.get();
	}

	/** From Fabricated's hook: the registries the reload's loot tables are read with, which Fabric's events take. */
	public static void loadingRegistries(HolderLookup.Provider registries) {
		loadingRegistries = registries;
	}

	/** The registries the current reload reads loot tables with, or none if it hasn't noted them. */
	public static HolderLookup.Provider loadingRegistriesOrEmpty() {
		HolderLookup.Provider registries = loadingRegistries;
		return registries != null ? registries : RegistryAccess.EMPTY;
	}

	/** From Fabricated's hook: the reload ({@code ReloadableServerRegistries.LoadResult}) is complete. */
	public static void allLoaded(ResourceManager manager, Object loadResult) {
		Registry<LootTable> registry = ((ReloadableServerRegistries.LoadResult) loadResult).layers().getLayer(RegistryLayer.RELOADABLE).lookupOrThrow(Registries.LOOT_TABLE);
		LootTableEvents.ALL_LOADED.invoker().onLootTablesLoaded(manager, registry);
		registry.listElements().forEach(reference -> LootCompat.setHolder(reference.value(), reference));
		loadingRegistries = null;
	}

	/** Fabric's {@code REPLACE}, then {@code MODIFY}, for one table: the table to load. */
	static LootTable modify(ResourceKey<LootTable> key, LootTable table, LootTableSource source, HolderLookup.Provider registries) {
		synchronized (HANDLERS) {
			LootTable replacement = LootTableEvents.REPLACE.invoker().replaceLootTable(key, table, source, registries);
			if (replacement != null) {
				table = replacement;
				source = LootTableSource.REPLACED;
			}
			LootTable.Builder builder = FabricLootTableBuilder.copyOf(table);
			LootTableEvents.MODIFY.invoker().modifyLootTable(key, builder, source, registries);
			return builder.build();
		}
	}

	/** Forge's event, where Forge loads the table its listeners leave (Forge 61 to 65). */
	static void onTableLoaded(LootTableLoadEvent event) {
		LootTable table = event.getTable();
		if (table == null) return;
		Identifier name = event.getName();
		LootTable result = modify(ResourceKey.create(Registries.LOOT_TABLE, name), table, source(name), loadingRegistriesOrEmpty());
		// Forge names each table (its global loot modifiers look tables up by name) before firing the event; a built
		// table has none yet.
		result.setLootTableId(name);
		event.setTable(result);
	}

	/** Where the table's file came from, as upstream decides it: the pack that provides the file in use. */
	private static LootTableSource source(Identifier id) {
		ResourceManager manager = reloadingManager.get();
		if (manager == null) return LootTableSource.DATA_PACK;
		return LootUtil.determineSource(manager.getResource(LOOT_TABLES.idToFile(id)).orElse(null));
	}
}
