package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
 * CI test mod part for Fabricated's native fabric-loot-api-v3 (Minecraft 1.21.1, whose Fabric API has no drop event):
 * dirt's loot table gets a diamond pool through MODIFY (and must come from vanilla), and dirt's drops must contain it.
 * MODIFY also names dirt's table as its builder builds it, as Porting Lib Loot names every table
 * ({@link LootNamedAsBuilt}), and the table must load with that name, where NeoForge refuses a second name (CORPUS-90).
 */
public final class LootTest implements ModInitializer {
	private static final ResourceLocation DIRT_ID = ResourceLocation.withDefaultNamespace("blocks/dirt");
	private static final ResourceKey<LootTable> DIRT = ResourceKey.create(Registries.LOOT_TABLE, DIRT_ID);
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

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
			((LootNamedAsBuilt) builder).fabricatedTest$nameAsBuilt(DIRT_ID);
		});
		LootTableEvents.ALL_LOADED.register((manager, registry) -> {
			if (registry.containsKey(DIRT)) SEEN.add("all_loaded");
			for (LootTable table : registry) {
				if (DIRT_ID.equals(registry.getKey(table)) && DIRT_ID.equals(table.getLootTableId())) SEEN.add("named");
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			List<String> failed = new ArrayList<>();
			for (String what : List.of("replace", "modify_vanilla", "all_loaded", "named")) {
				if (!SEEN.contains(what)) failed.add(what);
			}
			List<ItemStack> drops = Block.getDrops(Blocks.DIRT.defaultBlockState(), level, BlockPos.ZERO, null);
			if (drops.stream().noneMatch(stack -> stack.is(Items.DIRT))) failed.add("dirt_drop");
			if (drops.stream().noneMatch(stack -> stack.is(Items.DIAMOND))) failed.add("modify_drop");
			if (failed.isEmpty()) {
				TestMod.LOGGER.info("FABRICATED-TEST loot OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST loot FAILED: {} (seen {}, drops {})", failed, SEEN, drops);
			}
		});
	}
}
