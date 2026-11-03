package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.cmdralph.fabricated.api.FabricApiModule;
import io.github.cmdralph.fabricated.api.Fabricated;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;

/**
 * The test mod's part for Fabricated's native fabric-loot-api-v3 on Forge (F4), as main's LootTest: dirt's loot table
 * gets a diamond pool through MODIFY (and must come from vanilla), a drop listener adds an emerald, and dirt's drops
 * must contain both. Checked at the first server tick (the test mod has no Fabric lifecycle events on Forge). When
 * another mod provides the module (the stand-ins' run, ci/forge/standins.py), Fabricated's hooks into Forge's loot table
 * loading stand down with its copy of the module, so none of the loading events may come from them; the stand-in has no
 * hooks of its own (the drop event is upstream's mixin's, which the stand-in carries). MODIFY also names dirt's table
 * as its builder builds it, as Porting Lib Loot names every table ({@link LootNamedAsBuilt}), and the table must load
 * with that name, where Forge refuses a second name (CORPUS-90); where Porting Lib Loot is installed, it names the
 * table itself.
 */
final class ForgeLootTest {
	private static final Identifier DIRT_ID = Identifier.withDefaultNamespace("blocks/dirt");
	private static final ResourceKey<LootTable> DIRT = ResourceKey.create(Registries.LOOT_TABLE, DIRT_ID);
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
	/** Porting Lib Loot names every table as it is built (the corpus). */
	private static final boolean PORTING_LIB_LOOT = FabricLoader.getInstance().isModLoaded("porting_lib_loot");
	/**
	 * Whether the test mod names dirt's table as Porting Lib Loot would: not where Porting Lib Loot does, nor where another
	 * mod provides the loot module.
	 */
	private static final boolean NAMES_DIRT = !PORTING_LIB_LOOT && !providedElsewhere();

	private ForgeLootTest() {
	}

	static void register() {
		LootTableEvents.REPLACE.register((key, original, source, registries) -> {
			if (key.equals(DIRT)) SEEN.add("replace");
			return null;
		});
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!key.equals(DIRT)) return;
			SEEN.add(source == LootTableSource.VANILLA ? "modify_vanilla" : "modify_" + source);
			builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.DIAMOND)));
			if (NAMES_DIRT) ((LootNamedAsBuilt) builder).fabricatedForgeTest$nameAsBuilt(DIRT_ID);
		});
		LootTableEvents.ALL_LOADED.register((manager, registry) -> {
			if (registry.containsKey(DIRT)) SEEN.add("all_loaded");
			for (LootTable table : registry) {
				if (DIRT_ID.equals(registry.getKey(table)) && DIRT_ID.equals(table.getLootTableId())) SEEN.add("named");
			}
		});
		LootTableEvents.MODIFY_DROPS.register((holder, context, drops) -> {
			if (holder.is(DIRT)) drops.add(new ItemStack(Items.EMERALD));
		});
	}

	static void check(ServerLevel level) {
		if (providedElsewhere()) {
			List<String> fired = SEEN.stream().sorted().toList();
			if (fired.isEmpty()) {
				ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST loot left to its provider OK");
			} else {
				ForgeTestMod.LOGGER.error("FABRICATED-FORGE-TEST loot left to its provider FAILED: another mod provides fabric-loot-api-v3, yet {} fired", fired);
			}
			return;
		}
		List<String> failed = new ArrayList<>();
		List<String> expected = new ArrayList<>(List.of("replace", "modify_vanilla", "all_loaded"));
		if (NAMES_DIRT || PORTING_LIB_LOOT) expected.add("named");
		for (String what : expected) {
			if (!SEEN.contains(what)) failed.add(what);
		}
		List<ItemStack> drops = Block.getDrops(Blocks.DIRT.defaultBlockState(), level, BlockPos.ZERO, null);
		if (drops.stream().noneMatch(stack -> stack.is(Items.DIRT))) failed.add("dirt_drop");
		if (drops.stream().noneMatch(stack -> stack.is(Items.DIAMOND))) failed.add("modify_drop");
		if (drops.stream().noneMatch(stack -> stack.is(Items.EMERALD))) failed.add("modify_drops");
		if (failed.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST loot OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST loot not applied: {} (seen {}, drops {})", failed, SEEN, drops);
		}
	}

	private static boolean providedElsewhere() {
		return Fabricated.get().flatMap(api -> api.fabricApiModule("fabric-loot-api-v3"))
				.map(module -> module.status() == FabricApiModule.Status.PROVIDED_BY_MOD).orElse(false);
	}
}
