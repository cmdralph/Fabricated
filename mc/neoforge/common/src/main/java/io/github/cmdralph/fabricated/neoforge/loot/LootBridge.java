package io.github.cmdralph.fabricated.neoforge.loot;

import java.lang.ref.WeakReference;
import java.util.Optional;

import net.fabricmc.fabric.api.loot.v3.FabricLootTableBuilder;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.fabricmc.fabric.impl.loot.LootUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/**
 * Native {@code fabric-loot-api-v3}. Upstream's builder, accessor and drop mixins apply on NeoForge as they are and are
 * shipped unchanged; its hook into the loot table loading code does not (NeoForge rewrote that code), so:
 *
 * <ul>
 * <li>{@code REPLACE} and {@code MODIFY} run from NeoForge's {@link LootTableLoadEvent}, once per loot table, with the
 * source (vanilla, mod or data pack) of the file the table was read from, for one table at a time: NeoForge fires the
 * event on parallel workers (as upstream's hook runs, on 26.x), and mods' handlers aren't always safe to run at once
 * (Farmer's Delight builds a tag in its handler, which REI's mixin into the tag loader isn't safe for: run 381 crashed
 * loading registries on 26.3);</li>
 * <li>{@code ALL_LOADED} runs once the reload's loot table registry is complete, from Fabricated's hook on
 * {@code ReloadableServerRegistries.reload}, which also notes the resource manager being loaded from.</li>
 * </ul>
 */
public final class LootBridge {
	private static final FileToIdConverter LOOT_TABLES = FileToIdConverter.registry(Registries.LOOT_TABLE);
	private static volatile WeakReference<ResourceManager> reloadingManager = new WeakReference<>(null);
	/** Held while mods' handlers modify a table. */
	private static final Object HANDLERS = new Object();

	private LootBridge() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LootTableLoadEvent.class, LootBridge::onLoad);
	}

	/** From Fabricated's hook: a reload of the server's loot tables (and other reloadable registries) starts. */
	public static void startReload(ResourceManager manager) {
		reloadingManager = new WeakReference<>(manager);
	}

	/** From Fabricated's hook: the reload's loot table registry is complete. */
	public static void allLoaded(ResourceManager manager, Registry<LootTable> registry) {
		LootTableEvents.ALL_LOADED.invoker().onLootTablesLoaded(manager, registry);
		registry.listElements().forEach(reference -> LootCompat.setHolder(reference.value(), reference));
	}

	private static void onLoad(LootTableLoadEvent event) {
		ResourceKey<LootTable> key = event.getKey();
		HolderLookup.Provider registries = LootCompat.registries(event);
		LootTableSource source = source(event.getName());
		LootTable table = event.getTable();

		LootTable result;
		synchronized (HANDLERS) {
			LootTable replacement = LootTableEvents.REPLACE.invoker().replaceLootTable(key, table, source, registries);
			if (replacement != null) {
				table = replacement;
				source = LootTableSource.REPLACED;
			}
			LootTable.Builder builder = FabricLootTableBuilder.copyOf(table);
			LootTableEvents.MODIFY.invoker().modifyLootTable(key, builder, source, registries);
			result = builder.build();
		}
		// NeoForge names each table (its loot modifiers look tables up by name); a built table has none yet.
		result.setLootTableId(event.getName());
		event.setTable(result);
	}

	/** Where the table's file came from, as upstream decides it: the pack that provides the file in use. */
	private static LootTableSource source(Identifier id) {
		ResourceManager manager = reloadingManager.get();
		if (manager == null) return LootTableSource.DATA_PACK;
		Optional<Resource> resource = manager.getResource(LOOT_TABLES.idToFile(id));
		return LootUtil.determineSource(resource.orElse(null));
	}

}
