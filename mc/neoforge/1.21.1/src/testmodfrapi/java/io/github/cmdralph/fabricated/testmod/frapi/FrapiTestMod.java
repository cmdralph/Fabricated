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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Registers a block and its item for {@link FrapiClientTestMod}'s color providers (1.21.1). The test mod for Fabric
 * API's client modules on 1.21.1, which only the Fabric API jar provides: it joins CI's client run with Fabric API.
 *
 * <p>For Fabric's renderer API it also places, in front of the joining player, a block whose model is a Fabric model
 * and one whose vanilla multipart model has a Fabric model as its part, so the client meshes them into a chunk section,
 * and above them the two blocks whose Fabric models throw ({@link FrapiClientTestMod}). Then, until the game stops, it
 * keeps a copy of the first block falling beside them (drawn as a falling block), a sticky piston pushing and pulling
 * another copy behind them (drawn as a block a piston moves) and the first block breaking (drawn with the breaking
 * overlay).
 */
public final class FrapiTestMod implements ModInitializer {
	static final Logger LOGGER = LoggerFactory.getLogger("fabricated-testmod-frapi");
	static final String NAMESPACE = "fabricated_testmod_frapi";
	/** Ticks between two falling blocks, two piston moves, and two renewals of the breaking progress (which the client drops when stale). */
	private static final int RENEW_TICKS = 20;
	/** The id of the breaking entity: one no entity has, so every player is sent the progress. */
	private static final int BREAKER_ID = Integer.MIN_VALUE;
	/** How far each falling copy falls: into view, onto a support at eye level, whatever the terrain around the player. */
	private static final int FALL_HEIGHT = 4;
	static Block tintedBlock;
	static Item tintedItem;

	// Server thread only.
	private static @Nullable ServerLevel level;
	private static @Nullable BlockPos breaking;
	private static @Nullable BlockPos landing;
	/** Where the redstone block that powers the piston goes: the piston is above it, the block it moves above that. */
	private static @Nullable BlockPos pistonPower;
	private static int ticks;

	@Override
	public void onInitialize() {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "tinted_block");
		tintedBlock = Registry.register(BuiltInRegistries.BLOCK, id, new Block(BlockBehaviour.Properties.of()));
		tintedItem = Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(tintedBlock, new Item.Properties()));
		Block terrainBlock = register("terrain_block");
		Block nestedBlock = register("nested_block");
		Block brokenBlock = register("broken_block");
		Block brokenNestedBlock = register("broken_nested_block");
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> server.execute(() -> {
			Direction facing = listener.player.getDirection();
			BlockPos front = listener.player.blockPosition().relative(facing, 2).above();
			BlockPos nested = front.relative(facing.getClockWise());
			level = listener.player.serverLevel();
			level.setBlockAndUpdate(front, terrainBlock.defaultBlockState());
			level.setBlockAndUpdate(nested, nestedBlock.defaultBlockState());
			level.setBlockAndUpdate(front.above(), brokenBlock.defaultBlockState());
			level.setBlockAndUpdate(nested.above(), brokenNestedBlock.defaultBlockState());
			breaking = front;
			landing = front.relative(facing.getCounterClockWise());
			level.setBlockAndUpdate(landing.below(), Blocks.STONE.defaultBlockState());
			pistonPower = front.relative(facing, 2).below();
			level.setBlockAndUpdate(pistonPower.above(), Blocks.STICKY_PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
			level.setBlockAndUpdate(pistonPower.above(2), terrainBlock.defaultBlockState());
			for (int i = 3; i <= 4; i++) level.setBlockAndUpdate(pistonPower.above(i), Blocks.AIR.defaultBlockState());
			LOGGER.info("FABRICATED-TEST frapi block placed at {} (nested model block at {}, broken model blocks above them, piston at {})", front, nested,
					pistonPower.above());
		}));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (level == null || ticks++ % RENEW_TICKS != 0) return;
			level.destroyBlockProgress(BREAKER_ID, breaking, 5);
			// Cleared each time (the last copy landed in it), so every copy falls the whole way.
			for (int i = 0; i <= FALL_HEIGHT; i++) level.setBlockAndUpdate(landing.above(i), Blocks.AIR.defaultBlockState());
			FallingBlockEntity.fall(level, landing.above(FALL_HEIGHT), terrainBlock.defaultBlockState());
			// Powered every other time: the sticky piston pushes its block up, then pulls it back.
			boolean powered = level.getBlockState(pistonPower).is(Blocks.REDSTONE_BLOCK);
			level.setBlockAndUpdate(pistonPower, powered ? Blocks.AIR.defaultBlockState() : Blocks.REDSTONE_BLOCK.defaultBlockState());
		});
	}

	private static Block register(String path) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(NAMESPACE, path), new Block(BlockBehaviour.Properties.of()));
	}
}
