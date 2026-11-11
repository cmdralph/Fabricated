package io.github.cmdralph.fabricated.testmod.neoforge;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;

import io.github.cmdralph.fabricated.testmod.TestMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * A Fabric mod's layer put into vanilla's first HUD stack in {@code Gui}'s constructor, after the hotbar, as Apoli puts
 * its resource bars there: at the constructor's read of {@code layers}, which NeoForge 21.1's constructor never makes
 * (HUD-7). It must be a layer of NeoForge's own HUD, drawn once in a drawing of it, after NeoForge's spectator tooltip
 * layer and before its experience level layer (vanilla's hotbar layer is NeoForge's ten up to the tooltip), and not
 * while the GUI is hidden, as vanilla draws its first stack only then. Reported once, after a few frames drawn with the
 * GUI hidden.
 */
@Mixin(Gui.class)
public abstract class HudLayersMixin {
	@Unique
	private static final int HIDDEN_FRAMES = 3;
	/** What a drawing of the HUD drew: the name of each of NeoForge's layers as it starts, and "layer" where this one drew; on the render thread only. */
	@Unique
	private static final List<String> fabricatedTest$drawn = new ArrayList<>();
	/** Why a drawing with the GUI shown failed ("" where it passed), once one was made. */
	@Unique
	private static String fabricatedTest$shown;
	@Unique
	private static int fabricatedTest$hiddenFrames;
	@Unique
	private static boolean fabricatedTest$drawnHidden;
	@Unique
	private static boolean fabricatedTest$reported;

	@Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Gui;layers:Lnet/minecraft/client/gui/LayeredDraw;", opcode = Opcodes.GETFIELD))
	private void fabricatedTest$afterTheHotbar(Minecraft minecraft, CallbackInfo ci, @Local(ordinal = 0) LayeredDraw first) {
		((LayeredDrawLayersAccessor) first).fabricatedTest$layers().add(3, (graphics, deltaTracker) -> fabricatedTest$drawn.add("layer"));
		NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, event -> fabricatedTest$drawn.add(event.getName().toString()));
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void fabricatedTest$start(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		fabricatedTest$drawn.clear();
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void fabricatedTest$end(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (fabricatedTest$reported || client.player == null || fabricatedTest$drawn.isEmpty()) return;
		if (fabricatedTest$shown == null) {
			if (client.options.hideGui) return;
			fabricatedTest$shown = fabricatedTest$whereDrawn();
			client.options.hideGui = true;
			return;
		}
		fabricatedTest$drawnHidden |= fabricatedTest$drawn.contains("layer");
		if (++fabricatedTest$hiddenFrames < HIDDEN_FRAMES) return;
		client.options.hideGui = false;
		fabricatedTest$reported = true;
		String failed = !fabricatedTest$shown.isEmpty() ? fabricatedTest$shown : fabricatedTest$drawnHidden ? "drawn with the GUI hidden" : "";
		if (failed.isEmpty()) {
			TestMod.LOGGER.info("FABRICATED-TEST hud layer in vanilla's list OK");
		} else {
			TestMod.LOGGER.error("FABRICATED-TEST hud layer in vanilla's list FAILED: " + failed);
		}
	}

	/** Why the layer wasn't drawn once as a layer of NeoForge's, between the spectator tooltip and the experience level; "" where it was. */
	@Unique
	private static String fabricatedTest$whereDrawn() {
		List<String> drawn = fabricatedTest$drawn;
		int at = drawn.indexOf("layer");
		if (at < 0 || at != drawn.lastIndexOf("layer")) return "drawn " + (at < 0 ? "not at all" : "more than once") + ": " + drawn;
		boolean placed = at > 0 && !drawn.get(at - 1).startsWith("minecraft:")
				&& drawn.indexOf("minecraft:spectator_tooltip") < at && at < drawn.indexOf("minecraft:experience_level");
		return placed ? "" : "drawn elsewhere among NeoForge's layers: " + drawn;
	}
}
