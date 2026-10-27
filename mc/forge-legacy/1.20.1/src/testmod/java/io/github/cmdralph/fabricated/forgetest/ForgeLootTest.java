package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.loot.v1.FabricLootPool;
import net.fabricmc.fabric.api.loot.v1.FabricLootPoolBuilder;
import net.fabricmc.fabric.api.loot.v1.FabricLootSupplier;
import net.fabricmc.fabric.api.loot.v1.event.LootTableLoadingCallback;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.fabric.api.loot.v2.LootTableSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1802;
import net.minecraft.class_2960;
import net.minecraft.class_52;
import net.minecraft.class_55;
import net.minecraft.class_60;
import net.minecraft.class_77;
import net.minecraft.class_79;

/**
 * The forge-legacy test mod's part for Fabricated's native fabric-loot-api-v2 and fabric-loot-tables-v1 on Forge 47
 * (L4): Forge keeps a loot table's pools in a list, where the upstream modules read an array, and the tables' events
 * come from Fabricated's hook on the loot data manager. A pool added to dirt's table in MODIFY, and one through the
 * deprecated v1 event (its builders' methods called by upstream's names, which their own return type keeps out of
 * Intermediary's), must be in the table the game keeps, and the test mod's own table, replaced in REPLACE, must be the
 * replacement; each event must see where its table came from (vanilla's data, a mod's, a replacement). The tables are
 * read once all have loaded, through the v1 API, whose mixins read Forge's list too. With Fabric API's resource
 * conditions module, a table of the test mod's whose {@code fabric:load_conditions} name a mod that isn't there must not
 * load: upstream's hook into the loot data manager's parsing lambda, which Forge 47's class names otherwise, checks
 * them (LAMBDA-6). Fabric API's loot classes and the game's are named as a Fabric mod for 1.20.1 names them (the
 * stand-ins in src/testmodstubs).
 */
public final class ForgeLootTest {
	private static final class_2960 DIRT = new class_2960("minecraft", "blocks/dirt");
	/** The test mod's table (in its data), which REPLACE swaps for one without pools. */
	private static final class_2960 REPLACED = new class_2960(ForgeTestMod.MOD_ID, "fabric_replace");
	/** The test mod's table whose load conditions fail, which must not load with Fabric API's resource conditions module. */
	private static final class_2960 CONDITIONED = new class_2960(ForgeTestMod.MOD_ID, "fabric_conditioned");

	private static int dirtPools = -1;
	private static LootTableSource dirtReplaceSource;
	private static LootTableSource dirtModifySource;
	private static LootTableSource ownReplaceSource;
	private static LootTableSource ownModifySource;
	private static boolean conditionedLoaded;
	private static boolean logged;

	private ForgeLootTest() {
	}

	/**
	 * From the main entrypoint, as a Fabric mod registers its loot table events. The listeners run in the game's reload,
	 * which must go on whatever they find: a failure is logged and the check reports it.
	 */
	public static void register() {
		// v1's pool comes before MODIFY's: fabric-loot-tables-v1 applies it in its own MODIFY listener, the first.
		LootTableLoadingCallback.EVENT.register((resourceManager, lootManager, id, supplier, setter) -> {
			if (DIRT.equals(id)) {
				try {
					supplier.pool(FabricLootPoolBuilder.builder().with(class_77.method_411(class_1802.field_8831)));
				} catch (RuntimeException | LinkageError e) {
					ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST loot: the v1 event's pool failed", e);
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
					return class_52.method_324().method_338();
				}
			} catch (RuntimeException | LinkageError e) {
				ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST loot: REPLACE failed for {}", id, e);
			}
			return null;
		});
		LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
			try {
				if (DIRT.equals(id)) {
					dirtModifySource = source;
					tableBuilder.method_336(class_55.method_347().method_351(class_77.method_411(class_1802.field_20391)));
				} else if (REPLACED.equals(id)) {
					ownModifySource = source;
				} else if (CONDITIONED.equals(id)) {
					conditionedLoaded = true;
				}
			} catch (RuntimeException | LinkageError e) {
				ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST loot: MODIFY failed for {}", id, e);
			}
		});
		LootTableEvents.ALL_LOADED.register((resourceManager, lootManager) -> {
			if (logged) return;
			logged = true;
			try {
				check(lootManager);
			} catch (RuntimeException | LinkageError e) {
				ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST loot: reading the loaded tables failed", e);
			}
		});
	}

	private static void check(class_60 lootManager) {
		List<String> failed = new ArrayList<>();
		if (dirtReplaceSource != LootTableSource.VANILLA || dirtModifySource != LootTableSource.VANILLA) {
			failed.add("dirt's source " + dirtReplaceSource + " in REPLACE, " + dirtModifySource + " in MODIFY");
		}
		if (ownReplaceSource != LootTableSource.MOD || ownModifySource != LootTableSource.REPLACED) {
			failed.add("the test mod's table's source " + ownReplaceSource + " in REPLACE, " + ownModifySource + " in MODIFY");
		}
		List<class_55> pools = ((FabricLootSupplier) lootManager.getLootTable(DIRT)).getPools();
		if (dirtPools < 0 || pools.size() != dirtPools + 2) {
			failed.add("dirt's pools " + dirtPools + " before the v1 event and MODIFY, " + pools.size() + " after");
		} else {
			for (class_55 pool : pools.subList(dirtPools, pools.size())) {
				List<class_79> entries = ((FabricLootPool) pool).getEntries();
				if (entries.size() != 1 || !(entries.get(0) instanceof class_77)) failed.add("an added pool's entries " + entries);
			}
		}
		int replacedPools = ((FabricLootSupplier) lootManager.getLootTable(REPLACED)).getPools().size();
		if (replacedPools != 0) failed.add("the replaced table's pools " + replacedPools);
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST loot OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST loot: not in the tables the game keeps: {}", failed);
		}
		if (FabricLoader.getInstance().isModLoaded("fabric-resource-conditions-api-v1")) {
			if (conditionedLoaded) {
				ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST loot conditions: a table whose fabric:load_conditions fail was loaded");
			} else {
				ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST loot conditions OK");
			}
		}
	}
}
