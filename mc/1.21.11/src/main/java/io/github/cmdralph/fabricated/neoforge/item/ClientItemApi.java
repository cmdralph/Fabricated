package io.github.cmdralph.fabricated.neoforge.item;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Client-only part of the item API bridge; only loaded on a client. */
final class ClientItemApi {
	private ClientItemApi() {
	}

	static @Nullable Player player() {
		return Minecraft.getInstance().player;
	}
}
