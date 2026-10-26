package io.github.cmdralph.fabricated.testmod;

import java.util.List;

import net.fabricmc.fabric.api.item.v1.BlockTransformerEvents;
import net.fabricmc.fabric.api.item.v1.BlockTransformerHelper;
import net.fabricmc.fabric.api.registry.OxidizableBlocksRegistry;
import net.fabricmc.fabric.api.registry.VibrationFrequencyRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;

/**
 * {@link ContentRegistriesTest}'s registrations under 26.3's API (composting, stripping and fuel are data-driven there),
 * with block transformer registrations and their modify event.
 */
final class ContentRegistriesTestCompat {
	private static final BlockTransformer.BlockTransformData REGISTERED = BlockTransformer.BlockTransformData
			.builder(BlockPredicate.matchesBlocks(Blocks.DRIED_KELP_BLOCK), Blocks.DEAD_BRAIN_CORAL_BLOCK).build();
	private static final BlockTransformer.BlockTransformData ADDED = BlockTransformer.BlockTransformData
			.builder(BlockPredicate.matchesBlocks(Blocks.ANDESITE), Blocks.POLISHED_ANDESITE).build();

	private ContentRegistriesTestCompat() {
	}

	static void init(ResourceKey<GameEvent> event) {
		VibrationFrequencyRegistry.register(event, 7);
		OxidizableBlocksRegistry.registerWaxable(Blocks.STONE, Blocks.SMOOTH_STONE);
		BlockTransformerHelper.registerShovel(REGISTERED);
		BlockTransformerEvents.MODIFY.register((key, transforms, source, registries) -> {
			if (key.equals(BlockTransformers.HOE) && source.isBuiltIn()) transforms.add(ADDED);
		});
	}

	static void check(MinecraftServer server, List<String> failed) {
		Registry<BlockTransformer> transformers = server.registryAccess().lookupOrThrow(Registries.BLOCK_TRANSFORMER);
		if (!transformers.getOrThrow(BlockTransformers.SHOVEL).value().transforms().contains(REGISTERED)) failed.add("block_transformer_registered");
		if (!transformers.getOrThrow(BlockTransformers.HOE).value().transforms().contains(ADDED)) failed.add("block_transformer_modify");
	}
}
