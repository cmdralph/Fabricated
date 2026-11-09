package io.github.cmdralph.fabricated.testmod;

import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.fabric.api.registry.OxidizableBlocksRegistry;
import net.fabricmc.fabric.api.registry.SculkSensorFrequencyRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;

/** {@link ContentRegistriesTest}'s registrations under this version's API names, and the checks only it has. */
final class ContentRegistriesTestCompat {
	/** The checks another mod's fabric-content-registries-v0 answers differently, as its source reads (PROVIDER-8). */
	static final Map<String, String> PROVIDERS_ANSWERS = Map.of("vibration_frequency",
			"Forgified Fabric API's sculk sensor frequency registry writes vanilla's frequency table, which NeoForge 21.1 doesn't read: it reads its data map");

	private ContentRegistriesTestCompat() {
	}

	static void init(ResourceKey<GameEvent> event) {
		SculkSensorFrequencyRegistry.register(event, 7);
		OxidizableBlocksRegistry.registerWaxableBlockPair(Blocks.STONE, Blocks.SMOOTH_STONE);
		CompostingChanceRegistry.INSTANCE.add(Items.BEDROCK, 0.5f);
		StrippableBlockRegistry.register(Blocks.BASALT, Blocks.POLISHED_BASALT);
		FuelRegistry.INSTANCE.add(Items.BEDROCK, 1234);
	}

	@SuppressWarnings("deprecation")
	static void check(MinecraftServer server, List<String> failed) {
		if (ContentRegistriesPlatform.compostChance(new ItemStack(Items.BEDROCK)) != 0.5f) failed.add("compostable");
		if (AxeItem.getAxeStrippingState(Blocks.BASALT.defaultBlockState()) == null) failed.add("strippable");
		// What a furnace asks NeoForge 21.1 for (its data map, then its burn time event).
		if (new ItemStack(Items.BEDROCK).getBurnTime(RecipeType.SMELTING) != 1234) failed.add("fuel");
	}
}
