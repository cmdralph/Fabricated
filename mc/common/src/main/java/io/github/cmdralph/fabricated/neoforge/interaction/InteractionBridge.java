package io.github.cmdralph.fabricated.neoforge.interaction;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.block.BlockAttackInteractionAware;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.BlockEvents;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;

/**
 * Native {@code fabric-events-interaction-v0}: Fabric's interaction callbacks run from NeoForge's interaction events,
 * which sit at (nearly) the same points of the game's code. They run at {@link EventPriority#HIGHEST}, as upstream's
 * hooks run at the head of the methods NeoForge's events are fired from; a result other than {@code PASS} cancels
 * NeoForge's event with that result.
 *
 * <ul>
 * <li>{@code UseBlockCallback}: {@link PlayerInteractEvent.RightClickBlock} (both sides);</li>
 * <li>{@code UseItemCallback}: {@link PlayerInteractEvent.RightClickItem} (both sides);</li>
 * <li>{@code AttackBlockCallback}: {@link PlayerInteractEvent.LeftClickBlock} when a block starts being broken;</li>
 * <li>{@code AttackEntityCallback}: {@link AttackEntityEvent} (no hit result);</li>
 * <li>{@code UseEntityCallback}: NeoForge's entity interaction events, which differ per version
 * ({@link InteractionCompat});</li>
 * <li>{@code BlockEvents.USE_ITEM_ON} and {@code ItemEvents.USE_ON}: {@link UseItemOnBlockEvent}'s block and item
 * phases, fired at the head of {@code BlockState.useItemOn} and {@code ItemStack.useOn};</li>
 * <li>{@code PlayerBlockBreakEvents.BEFORE}/{@code CANCELED}: NeoForge's block break event (server side);
 * {@code AFTER} from an observing hook once the block is removed.</li>
 * </ul>
 *
 * <p>NeoForge has no event for {@code BlockEvents.USE_WITHOUT_ITEM}, {@code ItemEvents.USE} or pick-item requests;
 * small mixins fire those where upstream does. On the client, NeoForge fires its events after the interaction packet is
 * prepared, so the server always hears of a client-side cancelled interaction (and runs the callbacks there too);
 * upstream only sends it when the client result consumes the action.
 */
public final class InteractionBridge {
	private InteractionBridge() {
	}

	public static void register(Dist dist) {
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayerInteractEvent.RightClickBlock.class, InteractionBridge::onUseBlock);
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayerInteractEvent.RightClickItem.class, InteractionBridge::onUseItem);
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayerInteractEvent.LeftClickBlock.class, InteractionBridge::onAttackBlock);
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, AttackEntityEvent.class, InteractionBridge::onAttackEntity);
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, UseItemOnBlockEvent.class, InteractionBridge::onUseItemOn);
		InteractionCompat.register();
		registerRouter();
		if (dist.isClient()) ClientInteractionBridge.register();
	}

	/** Upstream's {@code InteractionEventsRouter}: the deprecated block attack interface, and client resync on cancelled breaks. */
	private static void registerRouter() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			BlockState state = level.getBlockState(pos);
			if ((Object) state instanceof BlockAttackInteractionAware stateAware) {
				if (stateAware.onAttackInteraction(state, level, pos, player, hand, direction)) return InteractionResult.FAIL;
			} else if (state.getBlock() instanceof BlockAttackInteractionAware blockAware) {
				if (blockAware.onAttackInteraction(state, level, pos, player, hand, direction)) return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		// The client may have removed the block already; a 3x3 area, as redstone updates reach that far.
		PlayerBlockBreakEvents.CANCELED.register((level, player, pos, state, blockEntity) -> {
			if (!(player instanceof ServerPlayer serverPlayer)) return;
			BlockPos corner = pos.offset(-1, -1, -1);
			for (int x = 0; x < 3; x++) {
				for (int y = 0; y < 3; y++) {
					for (int z = 0; z < 3; z++) {
						serverPlayer.connection.send(new ClientboundBlockUpdatePacket(level, corner.offset(x, y, z)));
					}
				}
			}
		});
	}

	private static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
		Player player = event.getEntity();
		// Upstream's client hook repeats the spectator check that follows it.
		if (event.getLevel().isClientSide() && player.isSpectator()) return;
		InteractionResult result = UseBlockCallback.EVENT.invoker().interact(player, event.getLevel(), event.getHand(), event.getHitVec());
		if (result != InteractionResult.PASS) {
			event.setCanceled(true);
			event.setCancellationResult(result);
		}
	}

	private static void onUseItem(PlayerInteractEvent.RightClickItem event) {
		InteractionResult result = UseItemCallback.EVENT.invoker().interact(event.getEntity(), event.getLevel(), event.getHand());
		if (result != InteractionResult.PASS) {
			event.setCanceled(true);
			event.setCancellationResult(result);
		}
	}

	private static void onAttackBlock(PlayerInteractEvent.LeftClickBlock event) {
		if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || event.getFace() == null) return;
		Player player = event.getEntity();
		Level level = event.getLevel();
		BlockPos pos = event.getPos();
		InteractionResult result = AttackBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, pos, event.getFace());
		if (result == InteractionResult.PASS) return;
		event.setCanceled(true);
		if (player instanceof ServerPlayer serverPlayer) {
			// The client might have broken the block on its side, so let it know.
			serverPlayer.connection.send(new ClientboundBlockUpdatePacket(level, pos));
			if (level.getBlockState(pos).hasBlockEntity()) {
				BlockEntity blockEntity = level.getBlockEntity(pos);
				if (blockEntity != null) {
					Packet<ClientGamePacketListener> update = blockEntity.getUpdatePacket();
					if (update != null) serverPlayer.connection.send(update);
				}
			}
		}
	}

	private static void onAttackEntity(AttackEntityEvent event) {
		Player player = event.getEntity();
		InteractionResult result = AttackEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, event.getTarget(), null);
		if (result != InteractionResult.PASS) event.setCanceled(true);
	}

	private static void onUseItemOn(UseItemOnBlockEvent event) {
		UseOnContext context = event.getUseOnContext();
		InteractionResult result = switch (event.getUsePhase()) {
			case BLOCK -> {
				Player player = event.getPlayer();
				if (player == null) yield null;
				Level level = event.getLevel();
				BlockPos pos = event.getPos();
				BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), pos, context.isInside());
				// NeoForge's context holds a copy of the stack; upstream passes the stack in hand.
				yield BlockEvents.USE_ITEM_ON.invoker().useItemOn(player.getItemInHand(event.getHand()), level.getBlockState(pos), level, pos, player,
						event.getHand(), hit);
			}
			case ITEM_AFTER_BLOCK -> ItemEvents.USE_ON.invoker().useOn(context);
			default -> null;
		};
		if (result != null) event.cancelWithResult(result);
	}

	/**
	 * Fabric's {@code UseEntityCallback}, for {@link InteractionCompat}.
	 *
	 * @param localPos the clicked point relative to the entity's position, or null when the interaction has none
	 * @return the result to cancel NeoForge's event with, or null to let the interaction go on
	 */
	static @Nullable InteractionResult useEntity(Player player, InteractionHand hand, Entity target, @Nullable Vec3 localPos) {
		EntityHitResult hit = localPos == null ? null : new EntityHitResult(target, localPos.add(target.getX(), target.getY(), target.getZ()));
		InteractionResult result = UseEntityCallback.EVENT.invoker().interact(player, player.level(), hand, target, hit);
		return result == InteractionResult.PASS ? null : result;
	}

	/**
	 * Fabric's {@code PlayerBlockBreakEvents.BEFORE} (and {@code CANCELED} when it refuses), for {@link InteractionCompat}.
	 * Server side only, as upstream.
	 *
	 * @return whether the block may be broken
	 */
	static boolean beforeBreak(Level level, Player player, BlockPos pos, BlockState state) {
		if (level.isClientSide()) return true;
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, blockEntity)) return true;
		PlayerBlockBreakEvents.CANCELED.invoker().onBlockBreakCanceled(level, player, pos, state, blockEntity);
		return false;
	}

	/** Fabric's {@code PlayerBlockBreakEvents.AFTER}, from Fabricated's hook once a player's break removed the block. */
	public static void afterBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, blockEntity);
	}
}
