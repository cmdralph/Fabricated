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
 * in its list (counted, and as its player list returns them), whether the client has a connection; at the end of each
 * tick whether it paused, each time that changes; and once, the mixins applied to it (in the stack's frames).
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
	@Unique
	private static String fabricatedTest$lastState;
	@Unique
	private static Boolean fabricatedTest$lastPaused;

	@Inject(method = "tickServer", at = @At("HEAD"), require = 0)
	private void fabricatedTest$pauseInputs(CallbackInfo ci) {
		IntegratedServer server = (IntegratedServer) (Object) this;
		Minecraft client = Minecraft.getInstance();
		String state = "client paused " + client.isPaused() + ", players " + server.getPlayerList().getPlayerCount() + " ("
				+ server.getPlayerList().getPlayers().size() + " returned) in the list " + System.identityHashCode(server.getPlayerList())
				+ " of server " + System.identityHashCode(server) + ", client connection " + (client.getConnection() != null);
		if (state.equals(fabricatedTest$lastState)) return;
		if (fabricatedTest$lastState == null) {
			TestMod.LOGGER.info("FABRICATED-TEST integrated server mixins", new Throwable("stack of tickServer"));
		}
		fabricatedTest$lastState = state;
		TestMod.LOGGER.info("FABRICATED-TEST integrated server tick {}: {}", server.getTickCount(), state);
	}

	@Inject(method = "tickServer", at = @At("RETURN"), require = 0)
	private void fabricatedTest$pauseResult(CallbackInfo ci) {
		IntegratedServer server = (IntegratedServer) (Object) this;
		boolean paused = server.isPaused();
		if (fabricatedTest$lastPaused != null && fabricatedTest$lastPaused == paused) return;
		fabricatedTest$lastPaused = paused;
		TestMod.LOGGER.info("FABRICATED-TEST integrated server tick {} ended {}", server.getTickCount(), paused ? "paused" : "running");
	}
}
