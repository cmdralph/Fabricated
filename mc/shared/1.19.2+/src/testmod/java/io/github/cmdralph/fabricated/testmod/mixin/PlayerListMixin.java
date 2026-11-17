package io.github.cmdralph.fabricated.testmod.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * Replaces the server's player list once it is constructed, as a mod in the Polyfrost stack does. NeoForge's
 * {@code getPlayers()} then returned a view of the old, empty list, and an integrated server finding no player stayed
 * paused (POLY-1): every client test's world now depends on Fabricated's {@code PlayerListViewMixin}.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
	@Shadow
	@Final
	@Mutable
	private List<ServerPlayer> players;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void fabricatedTest$replaceList(CallbackInfo ci) {
		this.players = new ArrayList<>(this.players);
	}
}
