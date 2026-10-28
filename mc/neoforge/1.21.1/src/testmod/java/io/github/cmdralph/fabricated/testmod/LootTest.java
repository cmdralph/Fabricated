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
 */
public final class LootTest implements ModInitializer {
	private static final ResourceKey<LootTable> DIRT = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace("blocks/dirt"));
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
		});
		LootTableEvents.ALL_LOADED.register((manager, registry) -> {
			if (registry.containsKey(DIRT)) SEEN.add("all_loaded");
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			List<String> failed = new ArrayList<>();
			for (String what : List.of("replace", "modify_vanilla", "all_loaded")) {
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
