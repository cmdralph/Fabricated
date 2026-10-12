package io.github.cmdralph.fabricated.testmod;

import java.util.List;

import net.fabricmc.fabric.api.registry.OxidizableBlocksRegistry;
import net.fabricmc.fabric.api.registry.VibrationFrequencyRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;

/** {@link ContentRegistriesTest}'s registrations under 26.3's API (composting, stripping and fuel are data-driven there). */
final class ContentRegistriesTestCompat {
	private ContentRegistriesTestCompat() {
	}

	static void init(ResourceKey<GameEvent> event) {
		VibrationFrequencyRegistry.register(event, 7);
		OxidizableBlocksRegistry.registerWaxable(Blocks.STONE, Blocks.SMOOTH_STONE);
	}

	static void check(MinecraftServer server, List<String> failed) {
	}
}
