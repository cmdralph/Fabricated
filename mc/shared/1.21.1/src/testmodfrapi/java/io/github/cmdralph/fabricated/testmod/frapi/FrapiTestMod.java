package io.github.cmdralph.fabricated.testmod.frapi;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 * another copy behind them (drawn as a block a piston moves, every few ticks: {@link #PISTON_TICKS}) and the first
 * block breaking (drawn with the breaking overlay). Behind the first block goes a block whose Fabric model wraps one that throws, as Continuity's models wrap
 * others; it is put back every other renewal, so its chunk section is meshed again after the wrapped model threw.
 *
 * <p>It also gives the joining player an item whose model is a Fabric model, held in their main hand (drawn in hand and
 * in the hotbar), and puts one in an item frame on the first block and one on the ground beside the frame, which no
 * one can pick up and which never despawns (drawn as an item in a frame and as a dropped item). With Continuity installed,
 * a glass wall goes above the blocks, for its connected textures.
 */
public final class FrapiTestMod implements ModInitializer {
	static final Logger LOGGER = LoggerFactory.getLogger("fabricated-testmod-frapi");
	static final String NAMESPACE = "fabricated_testmod_frapi";
	/** Ticks between two falling blocks and two renewals of the breaking progress (which the client drops when stale). */
	private static final int RENEW_TICKS = 20;
	/**
	 * Ticks between two moves of the piston, a little more than one move takes (three ticks). The client draws a block a
	 * piston moves only while its mesh of the chunk section holds the piston's moving block (from the section's rebuild
	 * after the move starts to the one after it ends), and it stays in the world for a few seconds only, the first ones
	 * spent building sections, the broken models' throws among them: with a move a second, run 41466902004 drew none, as
	 * Forge 47's run 41421652538 did.
	 */
	private static final int PISTON_TICKS = 4;
	/** The id of the breaking entity: one no entity has, so every player is sent the progress. */
	private static final int BREAKER_ID = Integer.MIN_VALUE;
	/** How far each falling copy falls: into view, onto a support at eye level, whatever the terrain around the player. */
	private static final int FALL_HEIGHT = 4;
	static Block tintedBlock;
	static Item tintedItem;
	/** An item whose model {@link FrapiClientTestMod} makes a Fabric model. */
	static Item fabricItem;
	/** A block whose Fabric model wraps one that throws ({@link FrapiClientTestMod}). */
	private static Block wrappedBlock;

	// Server thread only.
	private static @Nullable ServerLevel level;
	private static @Nullable BlockPos breaking;
	private static @Nullable BlockPos landing;
	/** Where the redstone block that powers the piston goes: the piston is above it, the block it moves above that. */
	private static @Nullable BlockPos pistonPower;
	/** Where the block whose Fabric model wraps a broken one goes. */
	private static @Nullable BlockPos wrapped;
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
		wrappedBlock = register("wrapped_broken_block");
		fabricItem = Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(NAMESPACE, "fabric_item"), new Item(new Item.Properties()));
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> server.execute(() -> {
			Direction facing = listener.player.getDirection();
			BlockPos front = listener.player.blockPosition().relative(facing, 2).above();
			BlockPos nested = front.relative(facing.getClockWise());
			level = listener.player.serverLevel();
			level.setBlockAndUpdate(front, terrainBlock.defaultBlockState());
			level.setBlockAndUpdate(nested, nestedBlock.defaultBlockState());
			level.setBlockAndUpdate(front.above(), brokenBlock.defaultBlockState());
			level.setBlockAndUpdate(nested.above(), brokenNestedBlock.defaultBlockState());
			wrapped = front.relative(facing);
			level.setBlockAndUpdate(wrapped, wrappedBlock.defaultBlockState());
			breaking = front;
			landing = front.relative(facing.getCounterClockWise());
			level.setBlockAndUpdate(landing.below(), Blocks.STONE.defaultBlockState());
			pistonPower = front.relative(facing, 2).below();
			level.setBlockAndUpdate(pistonPower.above(), Blocks.STICKY_PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
			level.setBlockAndUpdate(pistonPower.above(2), terrainBlock.defaultBlockState());
			for (int i = 3; i <= 4; i++) level.setBlockAndUpdate(pistonPower.above(i), Blocks.AIR.defaultBlockState());
			LOGGER.info("FABRICATED-TEST frapi block placed at {} (nested model block at {}, broken model blocks above them, wrapped broken model"
					+ " block at {}, piston at {})", front, nested, wrapped, pistonPower.above());
			placeItems(level, listener.player, front, facing);
			if (FabricLoader.getInstance().isModLoaded("continuity")) placeGlass(level, front.above(2), facing.getClockWise());
		}));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (level == null) return;
			int tick = ticks++;
			if (tick % PISTON_TICKS == 0) {
				// Powered every other time: the sticky piston pushes its block up, then pulls it back.
				boolean powered = level.getBlockState(pistonPower).is(Blocks.REDSTONE_BLOCK);
				level.setBlockAndUpdate(pistonPower, powered ? Blocks.AIR.defaultBlockState() : Blocks.REDSTONE_BLOCK.defaultBlockState());
			}
			if (tick % RENEW_TICKS != 0) return;
			level.destroyBlockProgress(BREAKER_ID, breaking, 5);
			// Cleared each time (the last copy landed in it), so every copy falls the whole way.
			for (int i = 0; i <= FALL_HEIGHT; i++) level.setBlockAndUpdate(landing.above(i), Blocks.AIR.defaultBlockState());
			FallingBlockEntity.fall(level, landing.above(FALL_HEIGHT), terrainBlock.defaultBlockState());
			// Taken away and put back in turn: its chunk section is meshed again, with it, after the wrapped model threw.
			level.setBlockAndUpdate(wrapped, level.getBlockState(wrapped).is(wrappedBlock) ? Blocks.AIR.defaultBlockState() : wrappedBlock.defaultBlockState());
		});
	}

	/**
	 * Gives the player the Fabric model item in their main hand, hangs one in an item frame on the face of {@code front}
	 * the player sees, and leaves one floating on the ground beside the frame, which never despawns and which no one can
	 * pick up.
	 */
	private static void placeItems(ServerLevel world, ServerPlayer player, BlockPos front, Direction facing) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(fabricItem));
		BlockPos framePos = front.relative(facing.getOpposite());
		BlockPos dropPos = framePos.relative(facing.getCounterClockWise());
		world.setBlockAndUpdate(framePos, Blocks.AIR.defaultBlockState());
		world.setBlockAndUpdate(dropPos, Blocks.AIR.defaultBlockState());
		ItemFrame frame = new ItemFrame(world, framePos, facing.getOpposite());
		frame.setItem(new ItemStack(fabricItem));
		world.addFreshEntity(frame);
		ItemEntity drop = new ItemEntity(world, dropPos.getX() + 0.5, dropPos.getY() + 0.25, dropPos.getZ() + 0.5, new ItemStack(fabricItem), 0, 0, 0);
		drop.setNeverPickUp();
		drop.setUnlimitedLifetime();
		drop.setNoGravity(true);
		world.addFreshEntity(drop);
		LOGGER.info("FABRICATED-TEST frapi items placed (in hand, in a frame at {}, dropped at {})", framePos, dropPos);
	}

	/**
	 * With Continuity installed (R6): a wall of glass two blocks wide and two high above the test blocks, which
	 * Continuity's default resource pack draws with connected textures ({@link FrapiClientTestMod} checks they are).
	 */
	private static void placeGlass(ServerLevel world, BlockPos corner, Direction across) {
		for (int up = 0; up < 2; up++) {
			for (int side = 0; side < 2; side++) world.setBlockAndUpdate(corner.above(up).relative(across, side), Blocks.GLASS.defaultBlockState());
		}
		LOGGER.info("FABRICATED-TEST continuity glass placed at {}", corner);
	}

	private static Block register(String path) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(NAMESPACE, path), new Block(BlockBehaviour.Properties.of()));
	}
}
