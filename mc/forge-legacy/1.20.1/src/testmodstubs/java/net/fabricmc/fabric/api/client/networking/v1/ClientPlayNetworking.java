package net.fabricmc.fabric.api.client.networking.v1;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.class_2540;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_634;

public final class ClientPlayNetworking {
	private ClientPlayNetworking() {
	}

	public static boolean registerGlobalReceiver(class_2960 channelName, PlayChannelHandler channelHandler) {
		throw new AssertionError("stand-in");
	}

	public static boolean canSend(class_2960 channelName) {
		throw new AssertionError("stand-in");
	}

	public static void send(class_2960 channelName, class_2540 buf) {
		throw new AssertionError("stand-in");
	}

	@FunctionalInterface
	public interface PlayChannelHandler {
		void receive(class_310 client, class_634 handler, class_2540 buf, PacketSender responseSender);
	}
}
