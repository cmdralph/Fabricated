package io.github.cmdralph.fabricated.testmod.frapi;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * CI test mod for Fabric's renderer API (26.x, client smoke test with Fabric API). When the player joins, it places
 * in front of them a block whose model is a Fabric model ({@link FrapiClientTestMod}) and a block whose multipart
 * model has a Fabric model as its part. Then, until the game stops, it keeps a copy of the first block falling beside
 * it (drawn as a moving block) and the first block breaking (drawn with the breaking overlay).
 */
public final class FrapiTestMod implements ModInitializer {
	static final Logger LOGGER = LoggerFactory.getLogger("fabricated-testmod-frapi");
	static final Identifier TERRAIN_BLOCK_ID = Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "terrain_block");
	static final Identifier NESTED_BLOCK_ID = Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "nested_block");
	/** A block whose blockstate uses one of NeoForge's blockstate model types, which must still parse with Fabric's model loading. */
	static final Identifier NEOFORGE_TYPE_BLOCK_ID = Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "neoforge_type_block");
	/** Ticks between two falling blocks, and between two renewals of the breaking progress (which the client drops when stale). */
	private static final int RENEW_TICKS = 20;
	/** The id of the breaking entity: one no entity has, so every player is sent the progress. */
	private static final int BREAKER_ID = Integer.MIN_VALUE;
	/** How far each falling copy falls: into view, onto a support at eye level, whatever the terrain around the player. */
	private static final int FALL_HEIGHT = 4;

	// Server thread only.
	private static @Nullable Level level;
	private static @Nullable BlockPos breaking;
	private static @Nullable BlockPos landing;
	private static int ticks;

	@Override
	public void onInitialize() {
		Block terrainBlock = register(TERRAIN_BLOCK_ID);
		Block nestedBlock = register(NESTED_BLOCK_ID);
		register(NEOFORGE_TYPE_BLOCK_ID);
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> server.execute(() -> {
			Direction facing = listener.player.getDirection();
			BlockPos front = listener.player.blockPosition().relative(facing, 2).above();
			BlockPos nested = front.relative(facing.getClockWise());
			level = listener.player.level();
			level.setBlockAndUpdate(front, terrainBlock.defaultBlockState());
			level.setBlockAndUpdate(nested, nestedBlock.defaultBlockState());
			breaking = front;
			landing = front.relative(facing.getCounterClockWise());
			level.setBlockAndUpdate(landing.below(), Blocks.STONE.defaultBlockState());
			LOGGER.info("FABRICATED-TEST frapi block placed at {} (nested model block at {})", front, nested);
		}));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (level == null || ticks++ % RENEW_TICKS != 0) return;
			level.destroyBlockProgress(BREAKER_ID, breaking, 5);
			// Cleared each time (the last copy landed in it), so every copy falls the whole way.
			for (int i = 0; i <= FALL_HEIGHT; i++) level.setBlockAndUpdate(landing.above(i), Blocks.AIR.defaultBlockState());
			FallingBlockEntity.fall(level, landing.above(FALL_HEIGHT), terrainBlock.defaultBlockState());
		});
	}

	private static Block register(Identifier id) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
		return Registry.register(BuiltInRegistries.BLOCK, key, new Block(BlockBehaviour.Properties.of().setId(key)));
	}
}
