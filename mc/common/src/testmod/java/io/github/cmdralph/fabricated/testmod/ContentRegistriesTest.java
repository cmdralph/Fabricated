package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;

/**
 * CI test mod part for Fabricated's native fabric-content-registries-v0: registrations made through Fabric's registries
 * must take effect where NeoForge reads them (flammability, waxing, vibration frequency; up to 26.2 also composting,
 * stripping and fuel, see {@link ContentRegistriesTestCompat}).
 */
public final class ContentRegistriesTest implements ModInitializer {
	private static final ResourceKey<GameEvent> EVENT = ResourceKey.create(Registries.GAME_EVENT, Identifier.fromNamespaceAndPath("fabricated_testmod", "test_event"));

	@Override
	public void onInitialize() {
		Holder<GameEvent> event = Registry.registerForHolder(BuiltInRegistries.GAME_EVENT, EVENT, new GameEvent(16));
		FlammableBlockRegistry.getDefaultInstance().add(Blocks.STONE, 5, 20);
		ContentRegistriesTestCompat.init(EVENT);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			List<String> failed = new ArrayList<>();
			if (!Blocks.STONE.defaultBlockState().isFlammable(level, BlockPos.ZERO, Direction.UP)) failed.add("flammable");
			if (HoneycombItem.getWaxed(Blocks.STONE.defaultBlockState()).map(state -> !state.is(Blocks.SMOOTH_STONE)).orElse(true)) failed.add("waxable");
			if (VibrationSystem.getGameEventFrequency(event) != 7) failed.add("vibration_frequency");
			ContentRegistriesTestCompat.check(server, failed);
			if (failed.isEmpty()) {
				TestMod.LOGGER.info("FABRICATED-TEST content registries OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST content registries FAILED: {}", failed);
			}
		});
	}
}
