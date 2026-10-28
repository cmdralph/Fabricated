package io.github.cmdralph.fabricated.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.common.CommonHooks;

/**
 * CI test mod part for Fabricated's native fabric-events-interaction-v0 (Minecraft 1.21.1, whose Fabric API has no block,
 * item or pick-item events): a Fabric fake player goes through the game's own interaction code (and NeoForge's hooks
 * where the game needs a client), and every Fabric callback must be seen and have its result honoured.
 */
public final class InteractionTest implements ModInitializer {
	private static final BlockPos BASE = new BlockPos(4, 250, 4);
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

	private static <T> T seen(String what, T result) {
		SEEN.add(what);
		return result;
	}

	@Override
	public void onInitialize() {
		UseItemCallback.EVENT.register((player, level, hand) -> player instanceof FakePlayer && player.getItemInHand(hand).is(Items.STICK)
				? seen("use_item", InteractionResultHolder.fail(player.getItemInHand(hand))) : InteractionResultHolder.pass(ItemStack.EMPTY));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> player instanceof FakePlayer && hit.getBlockPos().equals(BASE)
				? seen("use_block", InteractionResult.SUCCESS) : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> pos.equals(BASE)
				? seen("attack_block", InteractionResult.FAIL) : InteractionResult.PASS);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> player instanceof FakePlayer && entity instanceof ArmorStand
				? seen("attack_entity", InteractionResult.FAIL) : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> player instanceof FakePlayer && entity instanceof ArmorStand && hit != null
				? seen("use_entity", InteractionResult.FAIL) : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !pos.equals(BASE.above()) || seen("break_before", false));
		PlayerBlockBreakEvents.CANCELED.register((level, player, pos, state, blockEntity) -> {
			if (pos.equals(BASE.above())) SEEN.add("break_canceled");
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (pos.equals(BASE.above(2)) && state.is(Blocks.STONE)) SEEN.add("break_after");
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			List<String> failed = new ArrayList<>();
			for (BlockPos pos : List.of(BASE, BASE.east(), BASE.west(), BASE.above(), BASE.above(2))) {
				level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
			}
			FakePlayer player = FakePlayer.get(level);
			player.setPos(BASE.getX() + 2.5, BASE.getY() + 1, BASE.getZ() + 0.5);
			if (!player.isFakePlayer()) failed.add("fake_player");
			InteractionHand hand = InteractionHand.MAIN_HAND;

			ItemStack stick = new ItemStack(Items.STICK);
			player.setItemInHand(hand, stick);
			if (player.gameMode.useItem(player, level, stick, hand) != InteractionResult.FAIL) failed.add("use_item_result");
			if (player.gameMode.useItemOn(player, level, stick, hand, hit(BASE)) != InteractionResult.SUCCESS) failed.add("use_block_result");

			if (!CommonHooks.onLeftClickBlock(player, BASE, Direction.UP, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK).isCanceled()) {
				failed.add("attack_block_result");
			}
			ArmorStand stand = new ArmorStand(level, BASE.getX() + 0.5, BASE.getY() + 1, BASE.getZ() + 0.5);
			player.attack(stand);
			if (InteractionTestCompat.interact(player, stand) != InteractionResult.FAIL) failed.add("use_entity_result");

			player.setItemInHand(hand, ItemStack.EMPTY);
			if (player.gameMode.destroyBlock(BASE.above()) || !level.getBlockState(BASE.above()).is(Blocks.STONE)) failed.add("break_before_result");
			if (!player.gameMode.destroyBlock(BASE.above(2)) || !level.getBlockState(BASE.above(2)).isAir()) failed.add("break_result");

			for (String what : List.of("use_item", "use_block", "attack_block", "attack_entity", "use_entity", "break_before", "break_canceled",
					"break_after")) {
				if (!SEEN.contains(what)) failed.add(what);
			}
			for (BlockPos pos : List.of(BASE, BASE.east(), BASE.west(), BASE.above(), BASE.above(2))) {
				level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			}
			if (failed.isEmpty()) {
				TestMod.LOGGER.info("FABRICATED-TEST interaction events OK");
			} else {
				TestMod.LOGGER.error("FABRICATED-TEST interaction events FAILED: {}", failed);
			}
		});
	}

	private static BlockHitResult hit(BlockPos pos) {
		return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
	}
}
