package io.github.cmdralph.fabricated.forgetest.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;

import io.github.cmdralph.fabricated.forgetest.ForgeTestMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

/**
 * A Fabric mod's layer put into vanilla's HUD list in {@code Gui}'s constructor, after the hotbar, as Apoli puts its own
 * there: at the constructor's read of {@code layers}, which Forge 52's patch makes once more (VANILLA-4), into the list of
 * the first stack, which Forge 52 fills only as the constructor ends (HUD-6). It must be drawn once in a drawing of the
 * HUD, between the hotbar and the experience level, as vanilla draws it. Reported once.
 */
@Mixin(Gui.class)
public abstract class HudLayersMixin {
	/** What a drawing of the HUD drew, in order; on the render thread only. */
	@Unique
	private static final StringBuilder fabricatedForgeTest$drawn = new StringBuilder();
	@Unique
	private static boolean fabricatedForgeTest$reported;

	@Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Gui;layers:Lnet/minecraft/client/gui/LayeredDraw;", opcode = Opcodes.GETFIELD))
	private void fabricatedForgeTest$afterTheHotbar(Minecraft minecraft, CallbackInfo ci, @Local(ordinal = 0) LayeredDraw first) {
		((LayeredDrawLayersAccessor) first).fabricatedForgeTest$layers().add(3, (graphics, deltaTracker) -> fabricatedForgeTest$drawn.append("layer "));
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void fabricatedForgeTest$start(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		fabricatedForgeTest$drawn.setLength(0);
	}

	@Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"))
	private void fabricatedForgeTest$hotbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		fabricatedForgeTest$drawn.append("hotbar ");
	}

	@Inject(method = "renderExperienceLevel", at = @At("HEAD"))
	private void fabricatedForgeTest$experience(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		fabricatedForgeTest$drawn.append("experience ");
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void fabricatedForgeTest$end(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		String drawn = fabricatedForgeTest$drawn.toString().trim();
		if (fabricatedForgeTest$reported || !drawn.contains("hotbar")) return;
		fabricatedForgeTest$reported = true;
		if (drawn.equals("hotbar layer experience")) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST hud layer in vanilla's list OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST hud layer in vanilla's list FAILED: a drawing of the HUD drew " + drawn);
		}
	}
}
