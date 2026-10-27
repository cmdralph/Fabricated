package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.class_2540;
import net.minecraft.class_2960;

public interface PacketSender {
	default void sendPacket(class_2960 channel, class_2540 buf) {
		throw new AssertionError("stand-in");
	}
}
