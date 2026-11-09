package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.cmdralph.fabricated.api.FabricApiModule;
import io.github.cmdralph.fabricated.api.Fabricated;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
 * CI test mod part for Fabricated's native fabric-loot-api-v3: dirt's loot table gets a diamond pool through MODIFY
 * (and must come from vanilla), a drop listener adds an emerald, and dirt's drops must contain both. MODIFY also names
 * dirt's table as its builder builds it, as Porting Lib Loot names every table ({@link LootNamedAsBuilt}), and the table
 * must load with that name, where NeoForge refuses a second name before 26.3 (CORPUS-90).
 */
public final class LootTest implements ModInitializer {
	private static final Identifier DIRT_ID = Identifier.withDefaultNamespace("blocks/dirt");
	private static final ResourceKey<LootTable> DIRT = ResourceKey.create(Registries.LOOT_TABLE, DIRT_ID);
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
	/** Porting Lib Loot names every table as it is built (the corpus). */
	private static final boolean PORTING_LIB_LOOT = FabricLoader.getInstance().isModLoaded("porting_lib_loot");
	/**
	 * Whether the test mod names dirt's table as Porting Lib Loot would: not where Porting Lib Loot does, nor where another
	 * mod's loot module fires the events (Forgified Fabric API's, whose hook names the tables it rebuilds itself).
	 */
	private static final boolean PROVIDED = Fabricated.get().flatMap(api -> api.fabricApiModule("fabric-loot-api-v3"))
			.map(loot -> loot.status() == FabricApiModule.Status.PROVIDED_BY_MOD).orElse(false);
	private static final boolean NAMES_DIRT = !PORTING_LIB_LOOT && !PROVIDED;

	@Override
	public void onInitialize() {
		LootTableEvents.REPLACE.register((key, original, source, registries) -> {
			if (key.equals(DIRT)) SEEN.add("replace");
			return null;
		});
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!key.equals(DIRT)) return;
			SEEN.add(source == LootTableSource.VANILLA ? "modify_vanilla" : "modify_" + source);
			builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.DIAMOND)));
			if (NAMES_DIRT) ((LootNamedAsBuilt) builder).fabricatedTest$nameAsBuilt(DIRT_ID);
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

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (PROVIDED && SEEN.isEmpty()) {
				// A provider with no loading hooks of its own (the Forge branches' stand-in) leaves the events unfired.
				TestMod.LOGGER.info("FABRICATED-TEST loot SKIPPED: fabric-loot-api-v3 is another mod's, which fired none of its loading events");
				return;
			}
			ServerLevel level = server.overworld();
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
				TestMod.LOGGER.info("FABRICATED-TEST loot OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST loot FAILED: {} (seen {}, drops {})", failed, SEEN, drops);
			}
		});
	}
}
