package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * CI test mod part for a loot pool made with vanilla's constructor, which the test mod's access widener opens, as BCLib
 * makes the pools it merges (LOOT-3): compiled against vanilla's (the stand-in beside it), which NeoForge replaces with
 * one taking a name too. Once the server has started, the pool must roll its entry.
 */
public final class VanillaLootPoolTest implements ModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("fabricated_testmod");

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				LootPool pool = new LootPool(List.of(LootItem.lootTableItem(Items.DIAMOND).build()), List.of(), List.of(), ConstantValue.exactly(1),
						ConstantValue.exactly(0));
				List<ItemStack> drops = new ArrayList<>();
				pool.addRandomItems(drops::add, new LootContext.Builder(new LootParams.Builder(server.overworld()).create(LootContextParamSets.EMPTY))
						.create(Optional.empty()));
				if (drops.size() == 1 && drops.getFirst().is(Items.DIAMOND)) {
					LOGGER.info("FABRICATED-TEST vanilla loot pool OK");
				} else {
					LOGGER.error("FABRICATED-TEST vanilla loot pool FAILED: it rolled {}", drops);
				}
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error("FABRICATED-TEST vanilla loot pool FAILED", e);
			}
		});
	}
}
