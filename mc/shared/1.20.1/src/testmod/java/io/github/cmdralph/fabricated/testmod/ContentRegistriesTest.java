package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.fabric.api.registry.OxidizableBlocksRegistry;
import net.fabricmc.fabric.api.registry.SculkSensorFrequencyRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;

/**
 * 1.20.1 part of the CI test mod for fabric-content-registries-v0: registrations made through Fabric's registries must
 * take effect where the platform reads them (flammability, waxing, vibration frequency, composting, stripping and fuel),
 * as the later versions' test checks them. 1.20.1's game events are registered by value.
 */
public final class ContentRegistriesTest implements ModInitializer {
	@Override
	public void onInitialize() {
		GameEvent event = Registry.register(BuiltInRegistries.GAME_EVENT, TestCompat.id("fabricated_testmod", "test_event"), new GameEvent("fabricated_testmod_test_event", 16));
		FlammableBlockRegistry.getDefaultInstance().add(Blocks.STONE, 5, 20);
		SculkSensorFrequencyRegistry.register(event, 7);
		OxidizableBlocksRegistry.registerWaxableBlockPair(Blocks.STONE, Blocks.SMOOTH_STONE);
		CompostingChanceRegistry.INSTANCE.add(Items.BEDROCK, 0.5f);
		StrippableBlockRegistry.register(Blocks.BASALT, Blocks.POLISHED_BASALT);
		FuelRegistry.INSTANCE.add(Items.BEDROCK, 1234);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			List<String> failed = new ArrayList<>();
			if (!Blocks.STONE.defaultBlockState().isFlammable(level, BlockPos.ZERO, Direction.UP)) failed.add("flammable");
			if (HoneycombItem.getWaxed(Blocks.STONE.defaultBlockState()).map(state -> !state.is(Blocks.SMOOTH_STONE)).orElse(true)) failed.add("waxable");
			if (VibrationSystem.getGameEventFrequency(event) != 7) failed.add("vibration_frequency");
			if (ComposterBlock.COMPOSTABLES.getFloat(Items.BEDROCK) != 0.5f) failed.add("compostable");
			if (AxeItem.getAxeStrippingState(Blocks.BASALT.defaultBlockState()) == null) failed.add("strippable");
			// Vanilla's fuel table, which Fabric API's fuel registry fills on Fabric and furnaces read.
			if (AbstractFurnaceBlockEntity.getFuel().getOrDefault(Items.BEDROCK, 0) != 1234) failed.add("fuel");
			TestMod.report("content registries", failed, "fabric-content-registries-v0", Map.of("fuel",
					"Forgified Fabric API's fuel registry gives burn times through Forge's FurnaceFuelBurnTimeEvent, not vanilla's fuel table"));
		});
	}
}
