package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
		PlayerList list = (PlayerList) (Object) this;
		// The stack's frames name the mixins applied to the player list.
		TestMod.LOGGER.info("FABRICATED-TEST server placed a player ({} in the list {} of server {}, tick {})", list.getPlayerCount(),
				System.identityHashCode(list), System.identityHashCode(list.getServer()), list.getServer().getTickCount(),
				new Throwable("stack of placeNewPlayer"));
	}

	@Inject(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"))
	private void fabricatedTest$removing(CallbackInfo ci) {
		TestMod.LOGGER.info("FABRICATED-TEST server removing a player", new Throwable("stack of the removal"));
	}

	// Other ways a player leaves the list (not required: diagnostics only).
	@Inject(method = "respawn", at = @At("HEAD"), require = 0)
	private void fabricatedTest$respawning(CallbackInfoReturnable<?> cir) {
		TestMod.LOGGER.info("FABRICATED-TEST server respawning a player", new Throwable("stack of the call"));
	}

	@Inject(method = "removeAll", at = @At("HEAD"), require = 0)
	private void fabricatedTest$removingAll(CallbackInfo ci) {
		TestMod.LOGGER.info("FABRICATED-TEST server removing all players", new Throwable("stack of the call"));
	}
}
