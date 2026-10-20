package io.github.cmdralph.fabricated.testmod.frapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Places a block whose model is a Fabric model (its own {@code emitQuads}) in front of the joining player, so the client
 * meshes it into a chunk section (1.21.11).
 */
public final class FrapiTestMod implements ModInitializer {
	static final Logger LOGGER = LoggerFactory.getLogger("fabricated-testmod-frapi");
	static final Identifier TERRAIN_BLOCK_ID = Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "terrain_block");

	@Override
	public void onInitialize() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, TERRAIN_BLOCK_ID);
		Block terrainBlock = Registry.register(BuiltInRegistries.BLOCK, key, new Block(BlockBehaviour.Properties.of().setId(key)));
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> server.execute(() -> {
			BlockPos front = listener.player.blockPosition().relative(listener.player.getDirection(), 2).above();
			listener.player.level().setBlockAndUpdate(front, terrainBlock.defaultBlockState());
			LOGGER.info("FABRICATED-TEST frapi block placed at {}", front);
		}));
	}
}
