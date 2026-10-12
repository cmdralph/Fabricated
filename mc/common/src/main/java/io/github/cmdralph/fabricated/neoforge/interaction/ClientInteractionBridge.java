package io.github.cmdralph.fabricated.neoforge.interaction;

import org.jspecify.annotations.Nullable;

import io.github.cmdralph.fabricated.neoforge.mixin.client.KeyMappingAccessor;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client part of {@link InteractionBridge}. Fabric's {@code ClientPreAttackCallback} is asked once per tick while the
 * attack key is held or was clicked, as upstream asks it from the keybinding handling; a cancelling answer cancels
 * NeoForge's attack {@link InputEvent.InteractionKeyMappingTriggered} for that tick (no swing), and stops breaking a
 * block, as upstream does.
 */
public final class ClientInteractionBridge {
	private static boolean attackCancelled;

	private ClientInteractionBridge() {
	}

	static void register() {
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, event -> preAttack(Minecraft.getInstance()));
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, InputEvent.InteractionKeyMappingTriggered.class, event -> {
			if (!event.isAttack() || !attackCancelled) return;
			event.setCanceled(true);
			event.setSwingHand(false);
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.gameMode != null && minecraft.gameMode.isDestroying()) minecraft.gameMode.stopDestroyBlock();
		});
	}

	private static void preAttack(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			attackCancelled = false;
			return;
		}
		int clickCount = ((KeyMappingAccessor) minecraft.options.keyAttack).fabricated$getClickCount();
		attackCancelled = (minecraft.options.keyAttack.isDown() || clickCount != 0)
				&& ClientPreAttackCallback.EVENT.invoker().onClientPlayerPreAttack(minecraft, player, clickCount);
	}

	/** Fabric's {@code ClientPlayerBlockBreakEvents.AFTER}, from Fabricated's hook once the client removed a block. */
	public static void afterBreak(@Nullable ClientLevel level, @Nullable LocalPlayer player, BlockPos pos, BlockState state) {
		if (level == null || player == null) return;
		ClientPlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state);
	}
}
