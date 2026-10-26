package io.github.cmdralph.fabricated.testmod;

import java.util.List;

import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.fabricmc.fabric.api.registry.FuelRegistryEvents;
import net.fabricmc.fabric.api.registry.OxidizableBlocksRegistry;
import net.fabricmc.fabric.api.registry.SculkSensorFrequencyRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.gameevent.GameEvent;

/** {@link ContentRegistriesTest}'s registrations under this version's API names, and the checks only it has. */
final class ContentRegistriesTestCompat {
	private ContentRegistriesTestCompat() {
	}

	static void init(ResourceKey<GameEvent> event) {
		SculkSensorFrequencyRegistry.register(event, 7);
		OxidizableBlocksRegistry.registerWaxableBlockPair(Blocks.STONE, Blocks.SMOOTH_STONE);
		CompostingChanceRegistry.INSTANCE.add(Items.BEDROCK, 0.5f);
		StrippableBlockRegistry.register(Blocks.BASALT, Blocks.POLISHED_BASALT);
		FuelRegistryEvents.BUILD.register((builder, context) -> builder.add(Items.BEDROCK, 1234));
	}

	@SuppressWarnings("deprecation")
	static void check(MinecraftServer server, List<String> failed) {
		if (ComposterBlock.getValue(new ItemStack(Items.BEDROCK)) != 0.5f) failed.add("compostable");
		if (AxeItem.getAxeStrippingState(Blocks.BASALT.defaultBlockState()) == null) failed.add("strippable");
		if (server.fuelValues().burnDuration(new ItemStack(Items.BEDROCK)) != 1234) failed.add("fuel");
	}
}
