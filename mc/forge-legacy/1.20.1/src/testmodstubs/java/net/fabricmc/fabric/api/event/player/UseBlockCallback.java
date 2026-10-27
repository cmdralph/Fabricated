package net.fabricmc.fabric.api.event.player;

import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1657;
import net.minecraft.class_1937;
import net.minecraft.class_3965;

import net.fabricmc.fabric.api.event.Event;

@FunctionalInterface
public interface UseBlockCallback {
	Event<UseBlockCallback> EVENT = null;

	class_1269 interact(class_1657 player, class_1937 world, class_1268 hand, class_3965 hitResult);
}
