package net.minecraft.world.level.storage.loot;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;

/** Stand-in, never packaged: vanilla's loot pool as the test mod's vanilla calls name it (26.3). */
public class LootPool {
	public LootPool(List<LootPoolEntryContainer> entries, Optional<Holder<LootItemCondition>> condition, Optional<Holder<LootItemFunction>> modifier,
			Holder<ContextIntProvider> rolls, Holder<ContextFloatProvider> bonusRolls) {
	}

	public void addRandomItems(Consumer<ItemStack> output, LootContext context) {
	}
}
