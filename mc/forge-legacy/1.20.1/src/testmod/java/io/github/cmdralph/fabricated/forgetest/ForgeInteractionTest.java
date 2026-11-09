package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

/**
 * The forge-legacy test mod's part for fabric-events-interaction-v0's use and attack callbacks (with Fabric API's jar
 * only): a Fabric fake player goes through the game's own interaction code, which Forge 47's patches change where
 * upstream's hooks are, and each callback must be asked and its answer honoured. Using a stick ({@code useItem} of
 * its {@code gameMode}) fails, using the stone block ({@code useItemOn}) succeeds, attacking it
 * ({@code handleBlockBreakAction}) fails, and attacking ({@code attack}) and using (the server's handling of an interaction
 * packet, {@code handleInteract} of its {@code connection}) an armor stand both fail. The game finds an entity a player
 * uses by its id among the entities of chunks that track them, so the armor stand's part waits for the test site's
 * chunk, forced ({@code setChunkForced}), to tick its entities ({@code isPositionEntityTicking}), as the forge test mod's does; on a
 * client, mc-runtime-test waits for that part before it quits ({@link ClientHold}). Where another mod provides the
 * module (Forgified Fabric API), its answers are that mod's ({@link ForgeTestMod#report}). It passes a fake player where
 * the game takes a server player, so verifying it loads Fabric API's class: the test mod only touches it with the
 * interaction module installed.
 */
public final class ForgeInteractionTest {
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
	/**
	 * How long the armor stand's part waits for the site's chunk to tick entities, at most: 20 seconds. A client's
	 * integrated server on a CI runner, rendering in software, took more than 100 ticks once (#248's run 41563911536).
	 */
	private static final int MAX_WAIT = 400;
	private static final List<String> FAILED = new ArrayList<>();
	/** The stone block the callbacks answer for. Set by {@link #check}. */
	private static volatile BlockPos site;
	/** Ticks waited for the armor stand's part, or -1 when none is pending. */
	private static int waited = -1;

	private ForgeInteractionTest() {
	}

	private static <T> T seen(String what, T result) {
		SEEN.add(what);
		return result;
	}

	/** From the main entrypoint, as Fabric mods register their listeners; only with Fabric API's interaction module. */
	static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			return player instanceof FakePlayer && stack.is(Items.STICK)
					? seen("use item", InteractionResultHolder.fail(stack)) : InteractionResultHolder.pass(stack);
		});
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
		BlockPos pos = ForgeTestMod.testSite(level, 20, 284, 4);
		site = pos;
		level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
		FakePlayer player = player(level, pos);
		InteractionHand hand = InteractionHand.MAIN_HAND;
		ItemStack stick = new ItemStack(Items.STICK);
		player.setItemInHand(hand, stick);
		InteractionResult result = player.gameMode.useItem(player, level, stick, hand);
		if (result != InteractionResult.FAIL) FAILED.add("using the stick gave " + result);
		Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		result = player.gameMode.useItemOn(player, level, stick, hand, new BlockHitResult(center, Direction.UP, pos, false));
		if (result != InteractionResult.SUCCESS) FAILED.add("using the block gave " + result);
		player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, level.getMaxBuildHeight(), 0);
		player.setItemInHand(hand, ItemStack.EMPTY);
		level.setChunkForced(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), true);
		ClientHold.start();
		waited = 0;
	}

	/** At every later server tick: the armor stand's part, once the site's chunk ticks entities, and the result. */
	public static void tick(ServerLevel level) {
		if (waited < 0) return;
		BlockPos pos = site;
		if (!level.isPositionEntityTicking(pos) && ++waited < MAX_WAIT) return;
		waited = -1;
		try {
			finish(level, pos);
		} finally {
			ClientHold.done();
		}
	}

	/** The armor stand's part, when the site's chunk ticks entities, and the result. */
	private static void finish(ServerLevel level, BlockPos pos) {
		if (level.isPositionEntityTicking(pos)) {
			FakePlayer player = player(level, pos);
			ArmorStand stand = new ArmorStand(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
			level.addFreshEntity(stand);
			player.attack(stand);
			if (!stand.isAlive()) FAILED.add("the refused attack broke the armor stand");
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(stand, false, InteractionHand.MAIN_HAND, new Vec3(0, 1, 0)));
			stand.discard();
		} else {
			FAILED.add("the test site's chunk didn't tick entities within " + MAX_WAIT + " ticks");
		}
		level.setChunkForced(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), false);
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		for (String what : List.of("use item", "use block", "attack block", "attack entity", "use entity")) {
			if (!SEEN.contains(what)) FAILED.add("no " + what + " callback");
		}
		ForgeTestMod.report("interaction events", FAILED, "fabric-events-interaction-v0", Map.of("no use entity callback",
				"its FakePlayer is Forge's, whose connection drops every packet it is handed (FakePlayerNetHandler.handleInteract), so the armor stand"
						+ " is never used"));
	}

	/** The fake player next to the site, within reach of the block and the armor stand. */
	private static FakePlayer player(ServerLevel level, BlockPos pos) {
		FakePlayer player = FakePlayer.get(level);
		player.setPos(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5);
		return player;
	}
}
