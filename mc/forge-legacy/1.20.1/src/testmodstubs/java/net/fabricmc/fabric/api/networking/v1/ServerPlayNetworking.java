package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.class_2540;
import net.minecraft.class_2960;
import net.minecraft.class_3222;
import net.minecraft.class_3244;
import net.minecraft.server.MinecraftServer;

public final class ServerPlayNetworking {
	private ServerPlayNetworking() {
	}

	public static boolean registerGlobalReceiver(class_2960 channelName, PlayChannelHandler channelHandler) {
		throw new AssertionError("stand-in");
	}

	@FunctionalInterface
	public interface PlayChannelHandler {
		void receive(MinecraftServer server, class_3222 player, class_3244 handler, class_2540 buf, PacketSender responseSender);
	}
}
