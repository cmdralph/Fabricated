package net.fabricmc.fabric.api.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Compile-time stand-in: the members of Fabric API's 1.21.11 class that the forge test mod calls, in Mojang's names.
 */
public class FakePlayer extends ServerPlayer {
	private FakePlayer() {
		super(null, null, null, null);
		throw new AssertionError("stand-in");
	}

	public static FakePlayer get(ServerLevel world) {
		throw new AssertionError("stand-in");
	}
}
