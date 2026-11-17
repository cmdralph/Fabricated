package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forgetest.ForgeTestMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A Fabric mod's own hooks at the start and the end of vanilla's HUD method ({@code Gui.render}), which Forge 52 keeps
 * as vanilla's, its layers drawn through Forge's own layered draw (HUD-5): both must run once the world shows, where on
 * Forge 47 and 61 to 66 pre-flight points them elsewhere (HUD-4). The start hook cancels the first drawing it sees, as a
 * mod hiding the HUD does, so that drawing must stop there: the end hook must not run in it. Each is reported once; the
 * client smoke reports one never reported as missing.
 */
@Mixin(Gui.class)
public abstract class HudHooksMixin {
	/** The drawings the start hook saw; on the render thread only, as the rest. */
	@Unique
	private static int fabricatedForgeTest$started;
	@Unique
	private static boolean fabricatedForgeTest$endedCancelled;
	@Unique
	private static boolean fabricatedForgeTest$endReported;

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void fabricatedForgeTest$atTheStart(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		int started = ++fabricatedForgeTest$started;
		if (started == 1) {
			ci.cancel();
		} else if (started == 2) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST hud method start hook OK");
			if (fabricatedForgeTest$endedCancelled) {
				ForgeTestMod.LOGGER.error("FABRICATED-TEST hud method start hook cancels FAILED: the end hook ran in the drawing the start hook cancelled");
			} else {
				ForgeTestMod.LOGGER.info("FABRICATED-TEST hud method start hook cancels OK");
			}
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void fabricatedForgeTest$atTheEnd(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (fabricatedForgeTest$started == 1) {
			fabricatedForgeTest$endedCancelled = true;
		} else if (!fabricatedForgeTest$endReported) {
			fabricatedForgeTest$endReported = true;
			ForgeTestMod.LOGGER.info("FABRICATED-TEST hud method end hook OK");
		}
	}
}
