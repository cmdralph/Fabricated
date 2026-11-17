package io.github.cmdralph.fabricated.testmod.neoforge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.testmod.CharProbeScreen;
import io.github.cmdralph.fabricated.testmod.TestMod;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * A wrapper of the {@code GuiEventListener.charTyped} call vanilla's {@code KeyboardHandler.charTyped} lambdas make,
 * taking the listener and passing it on, as owo-lib's braid layers do; NeoForge 21.1's lambdas take the screen and call
 * the screen's {@code charTyped} (LAMBDA-14). Once no overlay is up, the keyboard handler is sent a character of each of
 * its lambdas (one char, and one taking two, a surrogate pair), as GLFW's callback sends them, to a screen of the check's
 * own ({@link CharProbeScreen}) it shows the handler meanwhile, so that no screen of the game's gets them (a world's
 * name box would): the wrapper must get each with that screen as the listener, and its original call must reach it.
 */
@Mixin(KeyboardHandler.class)
public abstract class ScreenCharTypedMixin {
	/** A character of one char, and one of two (U+1F600), which go through the two lambdas. */
	@Unique
	private static final int ONE_CHAR = 'x';
	@Unique
	private static final int TWO_CHARS = 0x1F600;
	/** The wrapper's calls with the probe screen as the listener: the one char, then each of the pair's two. */
	@Unique
	private static int fabricatedTest$wrapped;
	@Unique
	private static boolean fabricatedTest$sent;
	/** The screen shown to the handler while the characters are sent. */
	@Unique
	private static Screen fabricatedTest$probe;

	@Shadow
	private void charTyped(long window, int codepoint, int modifiers) {
	}

	@WrapOperation(method = {"lambda$charTyped$6", "lambda$charTyped$7"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/events/GuiEventListener;charTyped(CI)Z"))
	private static boolean fabricatedTest$typed(GuiEventListener listener, char typed, int modifiers, Operation<Boolean> original) {
		if (listener != null && listener == fabricatedTest$probe) fabricatedTest$wrapped++;
		return original.call(listener, typed, modifiers);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void fabricatedTest$send(CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (fabricatedTest$sent || client.getOverlay() != null) return;
		fabricatedTest$sent = true;
		long window = client.getWindow().getWindow();
		CharProbeScreen probe = new CharProbeScreen();
		Screen shown = client.screen;
		fabricatedTest$probe = probe;
		client.screen = probe;
		try {
			charTyped(window, ONE_CHAR, 0);
			charTyped(window, TWO_CHARS, 0);
		} finally {
			client.screen = shown;
			fabricatedTest$probe = null;
		}
		if (fabricatedTest$wrapped == 3 && probe.received() == 3) {
			TestMod.LOGGER.info("FABRICATED-TEST screen char typed wrapper OK");
		} else {
			TestMod.LOGGER.error("FABRICATED-TEST screen char typed wrapper FAILED: of the 3 characters sent, the wrapper saw {} with the screen, which got {}",
					fabricatedTest$wrapped, probe.received());
		}
	}
}
