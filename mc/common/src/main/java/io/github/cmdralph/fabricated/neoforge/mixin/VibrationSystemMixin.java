package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.core.Holder;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;

/**
 * NeoForge reads vibration frequencies from its data map only; Fabric's vibration frequency registry writes vanilla's
 * table. A game event the data map leaves out gets its frequency from that table, so data packs still win.
 */
@Mixin(VibrationSystem.class)
interface VibrationSystemMixin {
	@ModifyReturnValue(method = "getGameEventFrequency(Lnet/minecraft/core/Holder;)I", at = @At("RETURN"))
	private static int fabricated$tableFrequency(int frequency, Holder<GameEvent> event) {
		return frequency != 0 ? frequency : event.unwrapKey().map(VibrationSystem.VIBRATION_FREQUENCY_FOR_EVENT::applyAsInt).orElse(0);
	}
}
