package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * NeoForge's {@code getPlayers()} returns an unmodifiable view of the player list made when the list is constructed,
 * where vanilla returns the list itself. A Fabric mixin that replaces the list (written for vanilla, where both are
 * one object) leaves that view on the old, empty list: {@code getPlayerCount()} counts the players while
 * {@code getPlayers()} returns none, and an integrated server, finding no player, stays paused (POLY-1). The view
 * returned follows the list in the field, still unmodifiable.
 */
@Mixin(PlayerList.class)
abstract class PlayerListViewMixin {
	@Unique
	private static final Logger fabricated$LOGGER = LoggerFactory.getLogger("Fabricated");
	@Unique
	private static volatile boolean fabricated$reported;

	@Shadow
	@Final
	private List<ServerPlayer> players;

	// Written view first, read list first: a thread that sees the list sees its view.
	@Unique
	private volatile List<ServerPlayer> fabricated$view;
	@Unique
	private volatile List<ServerPlayer> fabricated$viewed;

	@ModifyReturnValue(method = "getPlayers", at = @At("RETURN"))
	private List<ServerPlayer> fabricated$viewOfCurrentList(List<ServerPlayer> neoForgeView) {
		List<ServerPlayer> current = players;
		if (fabricated$viewed != current) {
			fabricated$view = Collections.unmodifiableList(current);
			fabricated$viewed = current;
		}
		if (!fabricated$reported && neoForgeView.size() != current.size()) {
			fabricated$reported = true;
			fabricated$LOGGER.warn("The server's player list was replaced after NeoForge made its view of it (a mod's mixin); "
					+ "getPlayers() returns the replacement");
		}
		return fabricated$view;
	}
}
