package net.fabricmc.fabric.api.event.player;

import net.minecraft.class_1657;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_2586;
import net.minecraft.class_2680;

import net.fabricmc.fabric.api.event.Event;

public final class PlayerBlockBreakEvents {
	public static final Event<Before> BEFORE = null;
	public static final Event<After> AFTER = null;
	public static final Event<Canceled> CANCELED = null;

	private PlayerBlockBreakEvents() {
	}

	@FunctionalInterface
	public interface Before {
		boolean beforeBlockBreak(class_1937 world, class_1657 player, class_2338 pos, class_2680 state, class_2586 blockEntity);
	}

	@FunctionalInterface
	public interface After {
		void afterBlockBreak(class_1937 world, class_1657 player, class_2338 pos, class_2680 state, class_2586 blockEntity);
	}

	@FunctionalInterface
	public interface Canceled {
		void onBlockBreakCanceled(class_1937 world, class_1657 player, class_2338 pos, class_2680 state, class_2586 blockEntity);
	}
}
