package net.fabricmc.fabric.api.event.player;

import net.minecraft.class_1268;
import net.minecraft.class_1271;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_1937;

import net.fabricmc.fabric.api.event.Event;

@FunctionalInterface
public interface UseItemCallback {
	Event<UseItemCallback> EVENT = null;

	class_1271<class_1799> interact(class_1657 player, class_1937 world, class_1268 hand);
}
