package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.testmod.TestMod;
import net.minecraft.server.players.PlayerList;

/**
 * CI diagnostics for POLY-1: the Polyfrost clients reach their world while the integrated server has no player (it
 * pauses with none, so no chunk is ever sent). Logs when a player is being placed, when that finished, and who removes
 * one, with the stack.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
	@Inject(method = "placeNewPlayer", at = @At("HEAD"))
	private void fabricatedTest$placing(CallbackInfo ci) {
		TestMod.LOGGER.info("FABRICATED-TEST server placing a player");
	}

	@Inject(method = "placeNewPlayer", at = @At("RETURN"))
	private void fabricatedTest$placed(CallbackInfo ci) {
		TestMod.LOGGER.info("FABRICATED-TEST server placed a player ({} in the list)", ((PlayerList) (Object) this).getPlayerCount());
	}

	@Inject(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"))
	private void fabricatedTest$removing(CallbackInfo ci) {
		TestMod.LOGGER.info("FABRICATED-TEST server removing a player", new Throwable("stack of the removal"));
	}
}
