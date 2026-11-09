package io.github.cmdralph.fabricated.forge.interaction;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
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
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.listener.Priority;

/**
 * Fabric API's interaction callbacks (fabric-events-interaction-v0, passed through) at Forge 61 to 66's own hook points,
 * on the logical server. Forge posts its interaction events from code of its own, which its mods call too (a fake
 * player's left click, its entity interaction), and runs a server-side {@code ItemStack.useOn} through
 * {@code ForgeHooks.onPlaceItemIntoWorld}, where upstream's hooks look for vanilla's code:
 *
 * <ul>
 * <li>{@code AttackBlockCallback}: Forge's {@code LeftClickBlock} as a block starts being broken, at the head of the
 * server's block break handling, where upstream's hook sits; a result other than {@code PASS} cancels it, the client
 * told the block as it is, as upstream does;</li>
 * <li>{@code UseEntityCallback}: Forge's {@code EntityInteractSpecific}, with the clicked point; a result other than
 * {@code PASS} cancels it with that result;</li>
 * <li>{@code ItemEvents.USE_ON}: Forge's call of the item's {@code useOn} on the server ({@code ForgeHooksUseOnMixin});
 * the client's runs in the lambda Forge moved the call into, where pre-flight points upstream's hook.</li>
 * </ul>
 *
 * <p>The plans leave out upstream's server hooks for the first two, in the block break handling and the interaction
 * packet's handling, which run before Forge's events and would fire the callbacks a second time. The client keeps
 * upstream's hooks, and Forge's client events don't fire the callbacks.
 */
public final class InteractionEvents {
	/** Fabric API's interaction events module is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0");

	private InteractionEvents() {
	}

	public static void register() {
		if (!ENABLED) return;
		PlayerInteractEvent.LeftClickBlock.BUS.addListener(Priority.HIGHEST, Upstream::attackBlock);
		PlayerInteractEvent.EntityInteractSpecific.BUS.addListener(Priority.HIGHEST, Upstream::useEntity);
	}

	/** Fabric's {@code ItemEvents.USE_ON} where Forge's server calls the item's {@code useOn}: its result, or null to call it. */
	public static @Nullable InteractionResult useOn(UseOnContext context) {
		return ENABLED ? Upstream.useOn(context) : null;
	}

	/** Everything that names Fabric's interaction events module: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Upstream {
		private Upstream() {
		}

		static boolean attackBlock(PlayerInteractEvent.LeftClickBlock event) {
			Level level = event.getLevel();
			Player player = event.getEntity();
			BlockPos pos = event.getPos();
			if (level.isClientSide() || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || event.getFace() == null
					|| !player.isWithinBlockInteractionRange(pos, 1.0) || !level.isInWorldBounds(pos)) {
				return false;
			}
			InteractionResult result = AttackBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, pos, event.getFace());
			if (result == InteractionResult.PASS) return false;
			if (player instanceof ServerPlayer serverPlayer) {
				// The client may have broken the block on its side already.
				serverPlayer.connection.send(new ClientboundBlockUpdatePacket(level, pos));
				BlockEntity blockEntity = level.getBlockState(pos).hasBlockEntity() ? level.getBlockEntity(pos) : null;
				Packet<ClientGamePacketListener> update = blockEntity == null ? null : blockEntity.getUpdatePacket();
				if (update != null) serverPlayer.connection.send(update);
			}
			return true;
		}

		static boolean useEntity(PlayerInteractEvent.EntityInteractSpecific event) {
			Level level = event.getLevel();
			if (level.isClientSide()) return false;
			Entity target = event.getTarget();
			EntityHitResult hit = new EntityHitResult(target, event.getLocalPos().add(target.getX(), target.getY(), target.getZ()));
			InteractionResult result = UseEntityCallback.EVENT.invoker().interact(event.getEntity(), level, event.getHand(), target, hit);
			if (result == InteractionResult.PASS) return false;
			event.setCancellationResult(result);
			return true;
		}

		static @Nullable InteractionResult useOn(UseOnContext context) {
			return ItemEvents.USE_ON.invoker().useOn(context);
		}
	}
}
