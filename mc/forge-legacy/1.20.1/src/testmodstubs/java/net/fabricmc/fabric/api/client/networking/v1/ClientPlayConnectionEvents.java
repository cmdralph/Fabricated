package net.fabricmc.fabric.api.client.networking.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.class_310;
import net.minecraft.class_634;

public final class ClientPlayConnectionEvents {
	public static final Event<Join> JOIN = null;

	private ClientPlayConnectionEvents() {
	}

	@FunctionalInterface
	public interface Join {
		void onPlayReady(class_634 handler, PacketSender sender, class_310 client);
	}
}
