package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

/**
 * Upstream's guards for Fabric's fake players (only those): one sharing a real player's UUID must not take over that
 * player's advancement tracker, and fake players earn no advancements. From 26.2, NeoForge gives fake players a no-op
 * tracker of their own and these never trigger.
 */
@Mixin(PlayerAdvancements.class)
abstract class PlayerAdvancementsMixin {
	@Shadow
	private ServerPlayer player;

	@Inject(method = "setPlayer", at = @At("HEAD"), cancellable = true)
	private void fabricated$keepRealOwner(ServerPlayer newOwner, CallbackInfo ci) {
		if (newOwner instanceof FakePlayer) ci.cancel();
	}

	@Inject(method = "award", at = @At("HEAD"), cancellable = true)
	private void fabricated$noFakeAdvancements(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
		if (this.player instanceof FakePlayer) cir.setReturnValue(false);
	}
}
