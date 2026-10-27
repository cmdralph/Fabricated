package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The forge test mod's part for fabric-events-interaction-v0's use and attack callbacks (with Fabric API's jar only):
 * a Fabric fake player goes through the game's own interaction code, which Forge's patches change where upstream's hooks
 * are, and each callback must be asked and its answer honoured. Using a stick fails, using the stone block succeeds,
 * attacking it fails, and attacking and using an armor stand both fail. The game finds an entity a player uses by its id
 * among the entities of chunks that tick them, which none does at the first tick of a server without players, so the
 * armor stand's part waits for the test site's chunk, forced, to tick its entities.
 */
public final class ForgeInteractionTest {
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
	/** How long the armor stand's part waits for the site's chunk to tick entities. */
	private static final int MAX_WAIT = 100;
	private static final List<String> FAILED = new ArrayList<>();
	/** The stone block the callbacks answer for. Set by {@link #check}. */
	private static volatile BlockPos site;
	/** Ticks waited for the armor stand's part, or -1 when none is pending. */
	private static int waited = -1;

	private ForgeInteractionTest() {
	}

	private static InteractionResult seen(String what, InteractionResult result) {
		SEEN.add(what);
		return result;
	}

	/** From the main entrypoint, as Fabric mods register their listeners; only with Fabric API's interaction module. */
	public static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> player instanceof FakePlayer && player.getItemInHand(hand).is(Items.STICK)
				? seen("use item", InteractionResult.FAIL) : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> player instanceof FakePlayer && hit.getBlockPos().equals(site)
				? seen("use block", InteractionResult.SUCCESS) : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> player instanceof FakePlayer && pos.equals(site)
				? seen("attack block", InteractionResult.FAIL) : InteractionResult.PASS);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> player instanceof FakePlayer && entity instanceof ArmorStand
				? seen("attack entity", InteractionResult.FAIL) : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> player instanceof FakePlayer && entity instanceof ArmorStand
				? seen("use entity", InteractionResult.FAIL) : InteractionResult.PASS);
	}

	/** At the first server tick, on the overworld: the item and block uses, then the site's chunk forced for the rest. */
	public static void check(ServerLevel level) {
		BlockPos pos = ForgeTestMod.testSite(level, 20, 4);
		site = pos;
		level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
		FakePlayer player = player(level, pos);
		InteractionHand hand = InteractionHand.MAIN_HAND;
		ItemStack stick = new ItemStack(Items.STICK);
		player.setItemInHand(hand, stick);
		InteractionResult result = player.gameMode.useItem(player, level, stick, hand);
		if (result != InteractionResult.FAIL) FAILED.add("using the stick gave " + result);
		result = player.gameMode.useItemOn(player, level, stick, hand, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
		if (result != InteractionResult.SUCCESS) FAILED.add("using the block gave " + result);
		player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, level.getMaxY(), 0);
		player.setItemInHand(hand, ItemStack.EMPTY);
		level.setChunkForced(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), true);
		waited = 0;
	}

	/** At every later server tick: the armor stand's part, once the site's chunk ticks entities, and the result. */
	public static void tick(ServerLevel level) {
		if (waited < 0) return;
		BlockPos pos = site;
		if (!level.isPositionEntityTicking(pos) && ++waited < MAX_WAIT) return;
		waited = -1;
		if (level.isPositionEntityTicking(pos)) {
			FakePlayer player = player(level, pos);
			ArmorStand stand = new ArmorStand(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
			level.addFreshEntity(stand);
			player.attack(stand);
			if (!stand.isAlive()) FAILED.add("the refused attack broke the armor stand");
			ForgeInteractions.interact(player, stand, InteractionHand.MAIN_HAND);
			stand.discard();
		} else {
			FAILED.add("the test site's chunk didn't tick entities within " + MAX_WAIT + " ticks");
		}
		level.setChunkForced(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), false);
		level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		for (String what : List.of("use item", "use block", "attack block", "attack entity", "use entity")) {
			if (!SEEN.contains(what)) FAILED.add("no " + what + " callback");
		}
		if (FAILED.isEmpty()) {
			ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST interaction events OK");
		} else {
			ForgeTestMod.LOGGER.warn("FABRICATED-FORGE-TEST interaction events: {}", String.join("; ", FAILED));
		}
	}

	/**
	 * The fake player next to the site, its client counted as having loaded the world, as a client says once it has: the
	 * game ignores a player's entity interactions until then.
	 */
	private static FakePlayer player(ServerLevel level, BlockPos pos) {
		FakePlayer player = FakePlayer.get(level);
		player.setPos(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5);
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		return player;
	}
}
