package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.loot.v1.FabricLootPool;
import net.fabricmc.fabric.api.loot.v1.FabricLootPoolBuilder;
import net.fabricmc.fabric.api.loot.v1.FabricLootSupplier;
import net.fabricmc.fabric.api.loot.v1.event.LootTableLoadingCallback;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.fabric.api.loot.v2.LootTableSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;

/**
 * 1.20.1 part of the CI test mod for fabric-loot-api-v2 and its deprecated v1 (Fabricated's native modules on Forge 47): a
 * pool added to dirt's table in MODIFY, and one through the v1 event, must be in the table the game keeps, and the test
 * mod's own table, replaced in REPLACE, must be the replacement; each event must see where its table came from
 * (vanilla's data, a mod's, a replacement). The tables are read once all have loaded, through the v1 API. With Fabric
 * API's resource conditions module, a table of the test mod's whose {@code fabric:load_conditions} name a mod that isn't
 * there must not load. Dirt's table is also named as MODIFY's builder builds it, as Porting Lib Loot names every table
 * ({@link LootNamedAsBuilt}), and must load with that name, where the platform refuses a second name (CORPUS-90); where
 * Porting Lib Loot is installed (the corpus), it names the table itself. Where another mod provides fabric-loot-api-v2
 * (Forgified Fabric API), the sources its events give are that mod's answer, logged as such, and the rest,
 * fabric-loot-tables-v1 on its events included, must still hold (PROVIDER-14).
 */
public final class LootTest implements ModInitializer {
	private static final ResourceLocation DIRT = TestCompat.id("minecraft", "blocks/dirt");
	/** The test mod's table (in its data), which REPLACE swaps for one without pools. */
	private static final ResourceLocation REPLACED = TestCompat.id("fabricated_testmod", "fabric_replace");
	/** The test mod's table whose load conditions fail, which must not load with Fabric API's resource conditions module. */
	private static final ResourceLocation CONDITIONED = TestCompat.id("fabricated_testmod", "fabric_conditioned");

	/** Porting Lib Loot names every table as it is built (the corpus). */
	private static final boolean PORTING_LIB_LOOT = FabricLoader.getInstance().isModLoaded("porting_lib_loot");
	/** Whether another mod's loot module (Forgified Fabric API's) fires the events, in place of Fabricated's. */
	private static final boolean PROVIDED = TestMod.providedByAnotherMod("fabric-loot-api-v2");
	/**
	 * Whether the test mod names dirt's table as Porting Lib Loot would: not where Porting Lib Loot does, nor where another
	 * mod's loot module fires the events (Forgified Fabric API's hook names the tables it rebuilds itself).
	 */
	private static final boolean NAMES_DIRT = !PORTING_LIB_LOOT && !PROVIDED;

	private static int dirtPools = -1;
	private static LootTableSource dirtReplaceSource;
	private static LootTableSource dirtModifySource;
	private static LootTableSource ownReplaceSource;
	private static LootTableSource ownModifySource;
	private static boolean conditionedLoaded;
	private static boolean logged;

	/**
	 * The listeners run in the game's reload, which must go on whatever they find: a failure is logged and the check
	 * reports it.
	 */
	@Override
	public void onInitialize() {
		// v1's pool comes before MODIFY's: fabric-loot-tables-v1 applies it in its own MODIFY listener, the first.
		LootTableLoadingCallback.EVENT.register((resourceManager, lootManager, id, supplier, setter) -> {
			if (DIRT.equals(id)) {
				try {
					supplier.pool(FabricLootPoolBuilder.builder().with(LootItem.lootTableItem(Items.DIRT)));
				} catch (RuntimeException | LinkageError e) {
					TestMod.LOGGER.warn("FABRICATED-TEST loot: the v1 event's pool failed", e);
				}
			}
		});
		LootTableEvents.REPLACE.register((resourceManager, lootManager, id, original, source) -> {
			try {
				if (DIRT.equals(id)) {
					dirtReplaceSource = source;
					dirtPools = ((FabricLootSupplier) original).getPools().size();
				} else if (REPLACED.equals(id)) {
					ownReplaceSource = source;
					return LootTable.lootTable().build();
				}
			} catch (RuntimeException | LinkageError e) {
				TestMod.LOGGER.warn("FABRICATED-TEST loot: REPLACE failed for {}", id, e);
			}
			return null;
		});
		LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
			try {
				if (DIRT.equals(id)) {
					dirtModifySource = source;
					tableBuilder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.STONE)));
					if (NAMES_DIRT) ((LootNamedAsBuilt) tableBuilder).fabricatedTest$nameAsBuilt(id);
				} else if (REPLACED.equals(id)) {
					ownModifySource = source;
				} else if (CONDITIONED.equals(id)) {
					conditionedLoaded = true;
				}
			} catch (RuntimeException | LinkageError e) {
				TestMod.LOGGER.warn("FABRICATED-TEST loot: MODIFY failed for {}", id, e);
			}
		});
		LootTableEvents.ALL_LOADED.register((resourceManager, lootManager) -> {
			if (logged) return;
			logged = true;
			try {
				check(lootManager);
			} catch (RuntimeException | LinkageError e) {
				TestMod.LOGGER.error("FABRICATED-TEST loot FAILED: reading the loaded tables threw", e);
			}
		});
	}

	private static void check(LootDataManager lootManager) {
		List<String> failed = new ArrayList<>();
		// The sources are fabric-loot-api-v2's answer: another mod's module gives its own, kept apart from the failures.
		List<String> sources = PROVIDED ? new ArrayList<>() : failed;
		if (dirtReplaceSource != LootTableSource.VANILLA || dirtModifySource != LootTableSource.VANILLA) {
			sources.add("dirt's source " + dirtReplaceSource + " in REPLACE, " + dirtModifySource + " in MODIFY");
		}
		if (ownReplaceSource != LootTableSource.MOD || ownModifySource != LootTableSource.REPLACED) {
			sources.add("the test mod's table's source " + ownReplaceSource + " in REPLACE, " + ownModifySource + " in MODIFY");
		}
		LootTable dirt = lootManager.getLootTable(DIRT);
		if ((NAMES_DIRT || PORTING_LIB_LOOT) && !DIRT.equals(dirt.getLootTableId())) failed.add("dirt's table named " + dirt.getLootTableId());
		List<LootPool> pools = ((FabricLootSupplier) dirt).getPools();
		if (dirtPools < 0 || pools.size() != dirtPools + 2) {
			failed.add("dirt's pools " + dirtPools + " before the v1 event and MODIFY, " + pools.size() + " after");
		} else {
			for (LootPool pool : pools.subList(dirtPools, pools.size())) {
				List<LootPoolEntryContainer> entries = ((FabricLootPool) pool).getEntries();
				if (entries.size() != 1 || !(entries.get(0) instanceof LootItem)) failed.add("an added pool's entries " + entries);
			}
		}
		int replacedPools = ((FabricLootSupplier) lootManager.getLootTable(REPLACED)).getPools().size();
		if (replacedPools != 0) failed.add("the replaced table's pools " + replacedPools);
		if (!failed.isEmpty()) {
			TestMod.LOGGER.error("FABRICATED-TEST loot FAILED: not in the tables the game keeps: {}", failed);
		} else if (sources.isEmpty()) {
			TestMod.LOGGER.info("FABRICATED-TEST loot OK");
		} else {
			TestMod.LOGGER.info("FABRICATED-TEST loot SKIPPED: fabric-loot-api-v2 is another mod's, which answers {} as its source reads;"
					+ " the pools and the replaced table OK (PROVIDER-14)", sources);
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-resource-conditions-api-v1")) {
			if (conditionedLoaded) {
				TestMod.LOGGER.error("FABRICATED-TEST loot conditions FAILED: a table whose fabric:load_conditions fail was loaded");
			} else {
				TestMod.LOGGER.info("FABRICATED-TEST loot conditions OK");
			}
		}
	}
}
