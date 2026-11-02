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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * CI test mod for Fabric's renderer API (26.x, client smoke test with Fabric API). When the player joins, it places
 * in front of them a block whose model is a Fabric model ({@link FrapiClientTestMod}) and a block whose multipart
 * model has a Fabric model as its part, and above them the two blocks whose Fabric models throw. Then, until the game
 * stops, it keeps a copy of the first block falling beside it (drawn as a moving block) and the first block breaking
 * (drawn with the breaking overlay).
 *
 * <p>It also gives the joining player an item whose model is a Fabric model, held in their main hand (drawn in hand and
 * in the hotbar), and puts one in an item frame on the first block and one on the ground beside the frame, which no
 * one can pick up and which never despawns (drawn as an item in a frame and as a dropped item).
 */
public final class FrapiTestMod implements ModInitializer {
	static final Logger LOGGER = LoggerFactory.getLogger("fabricated-testmod-frapi");
	static final Identifier TERRAIN_BLOCK_ID = Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "terrain_block");
	static final Identifier NESTED_BLOCK_ID = Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "nested_block");
	/** Blocks whose Fabric model, or a Fabric part of whose multipart model, throws when drawn (FRAPI rule 8). */
	static final Identifier BROKEN_BLOCK_ID = Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "broken_block");
	static final Identifier BROKEN_NESTED_BLOCK_ID = Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "broken_nested_block");
	/** A block whose blockstate uses one of NeoForge's blockstate model types, which must still parse with Fabric's model loading. */
	static final Identifier NEOFORGE_TYPE_BLOCK_ID = Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "neoforge_type_block");
	/** An item whose model {@link FrapiClientTestMod} makes a Fabric model. */
	static final Identifier FABRIC_ITEM_ID = Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "fabric_item");
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
		Block brokenBlock = register(BROKEN_BLOCK_ID);
		Block brokenNestedBlock = register(BROKEN_NESTED_BLOCK_ID);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, FABRIC_ITEM_ID);
		Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, new Item(new Item.Properties().setId(itemKey)));
		register(NEOFORGE_TYPE_BLOCK_ID);
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> server.execute(() -> {
			Direction facing = listener.player.getDirection();
			BlockPos front = listener.player.blockPosition().relative(facing, 2).above();
			BlockPos nested = front.relative(facing.getClockWise());
			level = listener.player.level();
			level.setBlockAndUpdate(front, terrainBlock.defaultBlockState());
			level.setBlockAndUpdate(nested, nestedBlock.defaultBlockState());
			level.setBlockAndUpdate(front.above(), brokenBlock.defaultBlockState());
			level.setBlockAndUpdate(nested.above(), brokenNestedBlock.defaultBlockState());
			breaking = front;
			landing = front.relative(facing.getCounterClockWise());
			level.setBlockAndUpdate(landing.below(), Blocks.STONE.defaultBlockState());
			LOGGER.info("FABRICATED-TEST frapi block placed at {} (nested model block at {}, broken model blocks above them)", front, nested);
			placeItems(level, listener.player, item, front, facing);
		}));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (level == null || ticks++ % RENEW_TICKS != 0) return;
			level.destroyBlockProgress(BREAKER_ID, breaking, 5);
			// Cleared each time (the last copy landed in it), so every copy falls the whole way.
			for (int i = 0; i <= FALL_HEIGHT; i++) level.setBlockAndUpdate(landing.above(i), Blocks.AIR.defaultBlockState());
			// Glowing, so the client draws it a second time for its outline (M2b).
			FallingBlockEntity.fall(level, landing.above(FALL_HEIGHT), terrainBlock.defaultBlockState()).setGlowingTag(true);
		});
	}

	/**
	 * Gives the player the Fabric model item in their main hand, hangs one in an item frame on the face of {@code front}
	 * the player sees, and leaves one floating on the ground beside the frame, which never despawns and which no one can
	 * pick up.
	 */
	private static void placeItems(Level world, ServerPlayer player, Item item, BlockPos front, Direction facing) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
		BlockPos framePos = front.relative(facing.getOpposite());
		BlockPos dropPos = framePos.relative(facing.getCounterClockWise());
		world.setBlockAndUpdate(framePos, Blocks.AIR.defaultBlockState());
		world.setBlockAndUpdate(dropPos, Blocks.AIR.defaultBlockState());
		ItemFrame frame = new ItemFrame(world, framePos, facing.getOpposite());
		frame.setItem(new ItemStack(item));
		world.addFreshEntity(frame);
		ItemEntity drop = new ItemEntity(world, dropPos.getX() + 0.5, dropPos.getY() + 0.25, dropPos.getZ() + 0.5, new ItemStack(item), 0, 0, 0);
		drop.setNeverPickUp();
		drop.setUnlimitedLifetime();
		drop.setNoGravity(true);
		world.addFreshEntity(drop);
		LOGGER.info("FABRICATED-TEST frapi items placed (in hand, in a frame at {}, dropped at {})", framePos, dropPos);
	}

	private static Block register(Identifier id) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
		return Registry.register(BuiltInRegistries.BLOCK, key, new Block(BlockBehaviour.Properties.of().setId(key)));
	}
}
