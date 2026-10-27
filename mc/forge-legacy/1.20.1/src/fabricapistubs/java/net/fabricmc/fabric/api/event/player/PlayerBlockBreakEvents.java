package net.fabricmc.fabric.api.event.player;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.event.Event;

/**
 * Compile-time stand-in: the members of Fabric API's 1.20.1 class that Fabricated's in-game mod calls, in Mojang's
 * names.
 */
public final class PlayerBlockBreakEvents {
	public static final Event<Before> BEFORE = null;
	public static final Event<After> AFTER = null;
	public static final Event<Canceled> CANCELED = null;

	private PlayerBlockBreakEvents() {
	}

	@FunctionalInterface
	public interface Before {
		boolean beforeBlockBreak(Level world, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity);
	}

	@FunctionalInterface
	public interface After {
		void afterBlockBreak(Level world, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity);
	}

	@FunctionalInterface
	public interface Canceled {
		void onBlockBreakCanceled(Level world, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity);
	}
}
