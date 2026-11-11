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
 * Fabric mods' layers in vanilla's HUD stacks in {@code Gui}'s constructor, at its read of {@code layers}, which NeoForge
 * 21.1's constructor never makes (HUD-7): one put into the first stack after the hotbar, as Apoli puts its resource bars
 * there, and one replacing the second stack's first layer, vanilla's demo overlay. Each must be a layer of NeoForge's
 * own HUD, drawn once in a drawing of it where vanilla draws it: the first after NeoForge's spectator tooltip layer and
 * before its experience level layer (vanilla's hotbar layer is NeoForge's ten up to the tooltip), the replacement after
 * NeoForge's demo overlay layer, which no longer draws vanilla's, and before its debug overlay layer; and neither while
 * the GUI is hidden, as vanilla draws its stacks only then. Reported once, after a few frames drawn with the GUI hidden.
 */
@Mixin(Gui.class)
public abstract class HudLayersMixin {
	@Unique
	private static final int HIDDEN_FRAMES = 3;
	/**
	 * What a drawing of the HUD drew: the name of each of NeoForge's layers as it starts, "layer" and "replacement" where
	 * those drew, and "demo" where vanilla's demo overlay did; on the render thread only.
	 */
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
	private void fabricatedTest$addLayers(Minecraft minecraft, CallbackInfo ci, @Local(ordinal = 0) LayeredDraw first, @Local(ordinal = 1) LayeredDraw second) {
		((LayeredDrawLayersAccessor) first).fabricatedTest$layers().add(3, (graphics, deltaTracker) -> fabricatedTest$drawn.add("layer"));
		((LayeredDrawLayersAccessor) second).fabricatedTest$layers().set(0, (graphics, deltaTracker) -> fabricatedTest$drawn.add("replacement"));
		NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, event -> fabricatedTest$drawn.add(event.getName().toString()));
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void fabricatedTest$start(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		fabricatedTest$drawn.clear();
	}

	@Inject(method = "renderDemoOverlay", at = @At("HEAD"))
	private void fabricatedTest$demo(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		fabricatedTest$drawn.add("demo");
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void fabricatedTest$end(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (fabricatedTest$reported || client.player == null || fabricatedTest$drawn.isEmpty()) return;
		if (fabricatedTest$shown == null) {
			if (client.options.hideGui) return;
			String layer = fabricatedTest$whereDrawn("layer", "minecraft:spectator_tooltip", "minecraft:experience_level");
			String replacement = fabricatedTest$whereDrawn("replacement", "minecraft:demo_overlay", "minecraft:debug_overlay");
			fabricatedTest$shown = !layer.isEmpty() ? layer : !replacement.isEmpty() ? replacement
					: fabricatedTest$drawn.contains("demo") ? "vanilla's demo overlay layer, which a mod replaced, drawn too: " + fabricatedTest$drawn : "";
			client.options.hideGui = true;
			return;
		}
		fabricatedTest$drawnHidden |= fabricatedTest$drawn.contains("layer") || fabricatedTest$drawn.contains("replacement");
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

	/**
	 * Why {@code what} wasn't drawn once as a layer of NeoForge's of its own, after NeoForge's layer {@code after} and
	 * before its layer {@code before}; "" where it was.
	 */
	@Unique
	private static String fabricatedTest$whereDrawn(String what, String after, String before) {
		List<String> drawn = fabricatedTest$drawn;
		int at = drawn.indexOf(what);
		if (at < 0 || at != drawn.lastIndexOf(what)) return what + " drawn " + (at < 0 ? "not at all" : "more than once") + ": " + drawn;
		boolean placed = at > 0 && !drawn.get(at - 1).startsWith("minecraft:") && drawn.indexOf(after) < at && at < drawn.indexOf(before);
		return placed ? "" : what + " drawn elsewhere among NeoForge's layers: " + drawn;
	}
}
