package net.minecraft.world.level.storage.loot;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;

/** Stand-in, never packaged: vanilla's loot pool as the test mod's vanilla calls name it (1.21.1 to 26.2). */
public class LootPool {
	public LootPool(List<LootPoolEntryContainer> entries, List<LootItemCondition> conditions, List<LootItemFunction> functions,
			NumberProvider rolls, NumberProvider bonusRolls) {
	}

	public void addRandomItems(Consumer<ItemStack> output, LootContext context) {
	}
}
