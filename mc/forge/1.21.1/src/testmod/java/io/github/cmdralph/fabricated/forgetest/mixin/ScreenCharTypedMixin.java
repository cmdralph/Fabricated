package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forgetest.CharProbeScreen;
import io.github.cmdralph.fabricated.forgetest.ForgeTestMod;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * A wrapper of the {@code GuiEventListener.charTyped} call vanilla's {@code KeyboardHandler.charTyped} lambdas make,
 * taking the listener and passing it on, as owo-lib's braid layers do; Forge 52's lambdas take the screen and hand it the
 * character through {@code ForgeHooksClient.onScreenCharTyped}, which fires Forge's events around the screen's
 * {@code charTyped} (LAMBDA-14). Once no overlay is up, the keyboard handler is sent a character of each of
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
	private static int fabricatedForgeTest$wrapped;
	@Unique
	private static boolean fabricatedForgeTest$sent;
	/** The screen shown to the handler while the characters are sent. */
	@Unique
	private static Screen fabricatedForgeTest$probe;

	@Shadow
	private void charTyped(long window, int codepoint, int modifiers) {
	}

	@WrapOperation(method = {"lambda$charTyped$6", "lambda$charTyped$7"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/events/GuiEventListener;charTyped(CI)Z"))
	private static boolean fabricatedForgeTest$typed(GuiEventListener listener, char typed, int modifiers, Operation<Boolean> original) {
		if (listener != null && listener == fabricatedForgeTest$probe) fabricatedForgeTest$wrapped++;
		return original.call(listener, typed, modifiers);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void fabricatedForgeTest$send(CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (fabricatedForgeTest$sent || client.getOverlay() != null) return;
		fabricatedForgeTest$sent = true;
		long window = client.getWindow().getWindow();
		CharProbeScreen probe = new CharProbeScreen();
		Screen shown = client.screen;
		fabricatedForgeTest$probe = probe;
		client.screen = probe;
		try {
			charTyped(window, ONE_CHAR, 0);
			charTyped(window, TWO_CHARS, 0);
		} finally {
			client.screen = shown;
			fabricatedForgeTest$probe = null;
		}
		if (fabricatedForgeTest$wrapped == 3 && probe.received() == 3) {
			ForgeTestMod.LOGGER.info("FABRICATED-TEST screen char typed wrapper OK");
		} else {
			ForgeTestMod.LOGGER.error("FABRICATED-TEST screen char typed wrapper FAILED: of the 3 characters sent, the wrapper saw {} with the screen, which got {}",
					fabricatedForgeTest$wrapped, probe.received());
		}
	}
}
