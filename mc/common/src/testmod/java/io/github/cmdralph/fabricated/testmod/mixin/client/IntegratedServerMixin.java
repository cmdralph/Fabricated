package io.github.cmdralph.fabricated.testmod.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.testmod.TestMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

/**
 * CI diagnostics for POLY-1: the integrated server never ticks while a Polyfrost client is in its world. Logs, at the
 * start of each of its ticks, what decides whether it pauses, each time that changes: the client's pause, the players
 * in its list (and which list), whether the client has a connection.
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
	@Unique
	private static String fabricatedTest$lastState;

	@Inject(method = "tickServer", at = @At("HEAD"), require = 0)
	private void fabricatedTest$pauseInputs(CallbackInfo ci) {
		IntegratedServer server = (IntegratedServer) (Object) this;
		Minecraft client = Minecraft.getInstance();
		String state = "client paused " + client.isPaused() + ", players " + server.getPlayerList().getPlayerCount() + " in the list "
				+ System.identityHashCode(server.getPlayerList()) + " of server " + System.identityHashCode(server) + ", client connection "
				+ (client.getConnection() != null);
		if (state.equals(fabricatedTest$lastState)) return;
		fabricatedTest$lastState = state;
		TestMod.LOGGER.info("FABRICATED-TEST integrated server tick {}: {}", server.getTickCount(), state);
	}
}
