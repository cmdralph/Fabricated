package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forgetest.ForgeHudChecks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;

/**
 * A Fabric mod's own hooks into vanilla's HUD method, which Forge's patch hands to Forge's layers, returning before
 * vanilla's code (HUD-4): one at the method's end, which pre-flight points at its returns, Forge's included, and one at
 * its call drawing the crosshair, which Forge's crosshair layer makes and pre-flight points at the start of the called
 * method. Each must run once the world shows. The method still reaches its end on the loading screen, where Forge's
 * patch hands nothing over, so only an end outside it counts. (26.2 moved the HUD to {@code Hud}: common26late.)
 */
@Mixin(Gui.class)
public abstract class HudHooksMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void fabricatedForgeTest$atTheEnd(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (!(Minecraft.getInstance().screen instanceof LevelLoadingScreen)) ForgeHudChecks.passed("hud method end hook");
	}

	@Inject(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Gui;extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
	private void fabricatedForgeTest$atTheCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		ForgeHudChecks.passed("hud method call hook");
	}
}
