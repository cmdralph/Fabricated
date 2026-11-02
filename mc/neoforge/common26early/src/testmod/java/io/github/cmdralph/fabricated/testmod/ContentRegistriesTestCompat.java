package io.github.cmdralph.fabricated.testmod;

import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.registry.CompostableRegistry;
import net.fabricmc.fabric.api.registry.FuelValueEvents;
import net.fabricmc.fabric.api.registry.OxidizableBlocksRegistry;
import net.fabricmc.fabric.api.registry.VibrationFrequencyRegistry;
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
	/**
	 * The checks another mod's fabric-content-registries-v0 answers differently, as its source reads (PROVIDER-8; on 26.1.2
	 * and 26.2, PROVIDER-10).
	 */
	static final Map<String, String> PROVIDERS_ANSWERS = Map.of("vibration_frequency",
			"Forgified Fabric API's vibration frequency registry writes vanilla's frequency table, which NeoForge doesn't read: it reads its data map");

	private ContentRegistriesTestCompat() {
	}

	static void init(ResourceKey<GameEvent> event) {
		VibrationFrequencyRegistry.register(event, 7);
		OxidizableBlocksRegistry.registerWaxable(Blocks.STONE, Blocks.SMOOTH_STONE);
		CompostableRegistry.INSTANCE.add(Items.BEDROCK, 0.5f);
		StrippableBlockRegistry.register(Blocks.BASALT, Blocks.POLISHED_BASALT);
		FuelValueEvents.BUILD.register((builder, context) -> builder.add(Items.BEDROCK, 1234));
	}

	@SuppressWarnings("deprecation")
	static void check(MinecraftServer server, List<String> failed) {
		if (ComposterBlock.getValue(new ItemStack(Items.BEDROCK)) != 0.5f) failed.add("compostable");
		if (AxeItem.getAxeStrippingState(Blocks.BASALT.defaultBlockState()) == null) failed.add("strippable");
		if (server.fuelValues().burnDuration(new ItemStack(Items.BEDROCK)) != 1234) failed.add("fuel");
	}
}
