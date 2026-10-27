package io.github.cmdralph.fabricated.forge.interaction;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric API's block break events (fabric-events-interaction-v0, passed through) where Forge 47 breaks a player's block
 * (BREAK-1). Forge's {@code destroyBlock} no longer calls {@code Block.playerWillDestroy} and {@code Block.destroy}
 * itself, where upstream's hooks fire {@code BEFORE} and {@code AFTER}: both moved into a private {@code removeBlock}
 * Forge added. The plan leaves upstream's two hooks out, and {@code ServerPlayerGameModeBreakMixin} fires the events at
 * the same points of Forge's method instead, as on Forge 61 to 66.
 */
public final class BlockBreakEvents {
	/** Fabric API's interaction events module is installed. Its classes are only touched then. */
	private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("fabric-events-interaction-v0");

	private BlockBreakEvents() {
	}

	/**
	 * Fabric's {@code PlayerBlockBreakEvents.BEFORE}, and {@code CANCELED} when a listener refuses: always true without
	 * Fabric API's interaction events module.
	 *
	 * @return whether the block may be broken
	 */
	public static boolean before(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		return !ENABLED || Upstream.before(level, player, pos, state, blockEntity);
	}

	/** Fabric's {@code PlayerBlockBreakEvents.AFTER}, once a player's break removed the block. */
	public static void after(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (ENABLED) Upstream.after(level, player, pos, state, blockEntity);
	}

	/** Everything that names Fabric's interaction events module: loaded only once {@link #ENABLED} is known to be true. */
	private static final class Upstream {
		private Upstream() {
		}

		static boolean before(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
			if (PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, blockEntity)) return true;
			PlayerBlockBreakEvents.CANCELED.invoker().onBlockBreakCanceled(level, player, pos, state, blockEntity);
			return false;
		}

		static void after(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
			PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, blockEntity);
		}
	}
}
