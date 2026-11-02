package io.github.cmdralph.fabricated.testmod.frapi;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
 * (drawn with the breaking overlay). Behind the first block goes a block whose Fabric model wraps one that throws, as
 * Continuity's models wrap others; it is put back every other renewal, so its chunk section is meshed again after the
 * wrapped model threw.
 *
 * <p>It also gives the joining player an item whose model is a Fabric model, held in their main hand (drawn in hand and
 * in the hotbar), and puts one in an item frame on the first block and one on the ground beside the frame, which no
 * one can pick up and which never despawns (drawn as an item in a frame and as a dropped item).
 *
 * <p>And it summons two minecarts beside the blocks, held in the air: one carrying the first block, one carrying a block
 * whose Fabric model throws when drawn as an entity (each drawn as a block in an entity). With Continuity installed, a
 * glass wall goes above the blocks, for its connected textures.
 */
public final class FrapiTestMod implements ModInitializer {
	static final Logger LOGGER = LoggerFactory.getLogger("fabricated-testmod-frapi");
	static final Identifier TERRAIN_BLOCK_ID = Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "terrain_block");
	static final Identifier NESTED_BLOCK_ID = Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "nested_block");
	/** Blocks whose Fabric model, or a Fabric part of whose multipart model, throws when drawn (FRAPI rule 8). */
	static final Identifier BROKEN_BLOCK_ID = Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "broken_block");
	static final Identifier BROKEN_NESTED_BLOCK_ID = Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "broken_nested_block");
	/** A block whose Fabric model wraps one that throws when drawn (FRAPI rule 8, as Continuity's models wrap others). */
	static final Identifier WRAPPED_BROKEN_BLOCK_ID = Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "wrapped_broken_block");
	/** A block only ever shown in a minecart, whose Fabric model throws when drawn as an entity (FRAPI rule 8). */
	static final Identifier BROKEN_ENTITY_BLOCK_ID = Identifier.fromNamespaceAndPath("fabricated_testmod_frapi", "broken_entity_block");
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
	/** Where the block whose Fabric model wraps a broken one goes. */
	private static @Nullable BlockPos wrapped;
	private static int ticks;

	@Override
	public void onInitialize() {
		Block terrainBlock = register(TERRAIN_BLOCK_ID);
		Block nestedBlock = register(NESTED_BLOCK_ID);
		Block brokenBlock = register(BROKEN_BLOCK_ID);
		Block brokenNestedBlock = register(BROKEN_NESTED_BLOCK_ID);
		register(BROKEN_ENTITY_BLOCK_ID);
		Block wrappedBlock = register(WRAPPED_BROKEN_BLOCK_ID);
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
			wrapped = front.relative(facing);
			level.setBlockAndUpdate(wrapped, wrappedBlock.defaultBlockState());
			breaking = front;
			landing = front.relative(facing.getCounterClockWise());
			level.setBlockAndUpdate(landing.below(), Blocks.STONE.defaultBlockState());
			LOGGER.info("FABRICATED-TEST frapi block placed at {} (nested model block at {}, broken model blocks above them, wrapped broken model"
					+ " block at {})", front, nested, wrapped);
			placeItems(level, listener.player, item, front, facing);
			summonMinecarts(server, (ServerLevel) level, nested.relative(facing.getClockWise()));
			if (FabricLoader.getInstance().isModLoaded("continuity")) placeGlass(level, front.above(2), facing.getClockWise());
		}));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (level == null || ticks++ % RENEW_TICKS != 0) return;
			level.destroyBlockProgress(BREAKER_ID, breaking, 5);
			// Cleared each time (the last copy landed in it), so every copy falls the whole way.
			for (int i = 0; i <= FALL_HEIGHT; i++) level.setBlockAndUpdate(landing.above(i), Blocks.AIR.defaultBlockState());
			// Glowing, so the client draws it a second time for its outline (M2b).
			FallingBlockEntity.fall(level, landing.above(FALL_HEIGHT), terrainBlock.defaultBlockState()).setGlowingTag(true);
			// Taken away and put back in turn: its chunk section is meshed again, with it, after the wrapped model threw.
			level.setBlockAndUpdate(wrapped, level.getBlockState(wrapped).is(wrappedBlock) ? Blocks.AIR.defaultBlockState() : wrappedBlock.defaultBlockState());
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

	/**
	 * With Continuity installed (R6): a wall of glass two blocks wide and two high above the test blocks, which
	 * Continuity's default resource pack draws with connected textures ({@link FrapiClientTestMod} checks they are).
	 */
	private static void placeGlass(Level world, BlockPos corner, Direction across) {
		for (int up = 0; up < 2; up++) {
			for (int side = 0; side < 2; side++) world.setBlockAndUpdate(corner.above(up).relative(across, side), Blocks.GLASS.defaultBlockState());
		}
		LOGGER.info("FABRICATED-TEST continuity glass placed at {}", corner);
	}

	/**
	 * Summons a minecart carrying the terrain block at {@code pos} and one carrying the broken entity block above it, both
	 * without gravity, so they stay where they are drawn whatever the terrain. Through the summon command, as upstream's
	 * test of block models in minecarts does.
	 */
	private static void summonMinecarts(MinecraftServer server, ServerLevel world, BlockPos pos) {
		world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		world.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
		CommandSourceStack console = server.createCommandSourceStack().withLevel(world).withSuppressedOutput();
		if (summonMinecart(server, console, pos, TERRAIN_BLOCK_ID) && summonMinecart(server, console, pos.above(), BROKEN_ENTITY_BLOCK_ID)) {
			LOGGER.info("FABRICATED-TEST frapi minecarts summoned at {} (the terrain block) and above it (the broken entity block)", pos);
		}
	}

	private static boolean summonMinecart(MinecraftServer server, CommandSourceStack console, BlockPos pos, Identifier block) {
		// The block's id under both keys: 26.3 reads it from "id", 26.1.2 and 26.2 from "Name", and each ignores the other.
		String command = "summon minecraft:minecart " + (pos.getX() + 0.5) + " " + pos.getY() + " " + (pos.getZ() + 0.5)
				+ " {NoGravity:1b,Invulnerable:1b,DisplayState:{Name:\"" + block + "\",id:\"" + block + "\"}}";
		try {
			if (server.getCommands().getDispatcher().execute(command, console) == 1) return true;
			LOGGER.error("FABRICATED-TEST frapi minecart FAILED (not summoned: {})", command);
		} catch (CommandSyntaxException e) {
			LOGGER.error("FABRICATED-TEST frapi minecart FAILED ({}: {})", command, e.getMessage());
		}
		return false;
	}

	private static Block register(Identifier id) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
		return Registry.register(BuiltInRegistries.BLOCK, key, new Block(BlockBehaviour.Properties.of().setId(key)));
	}
}
