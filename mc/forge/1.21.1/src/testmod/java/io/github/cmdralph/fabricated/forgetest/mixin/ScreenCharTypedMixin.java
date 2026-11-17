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

/**
 * A wrapper of the {@code GuiEventListener.charTyped} call vanilla's {@code KeyboardHandler.charTyped} lambdas make,
 * taking the listener and passing it on, as owo-lib's braid layers do; Forge 52's lambdas take the screen and hand it the
 * character through {@code ForgeHooksClient.onScreenCharTyped}, which fires Forge's events around the screen's
 * {@code charTyped} (LAMBDA-14). Once no overlay is up, the keyboard handler is sent a character of each of
 * its lambdas (one char, and one taking two, a surrogate pair), as GLFW's callback sends them, to a screen of the check's
 * own ({@link CharProbeScreen}) it shows the handler meanwhile, so that no screen of the game's gets them (a world's
 * name box would): the wrapper must get each with that screen as the listener, and its original call must reach it.
 * The handlers keep no state of the mixin's own, as owo-lib's don't: the probe keeps it.
 */
@Mixin(KeyboardHandler.class)
public abstract class ScreenCharTypedMixin {
	/** A character of one char, and one of two (U+1F600), which go through the two lambdas. */
	@Unique
	private static final int ONE_CHAR = 'x';
	@Unique
	private static final int TWO_CHARS = 0x1F600;

	@Shadow
	private void charTyped(long window, int codepoint, int modifiers) {
	}

	@WrapOperation(method = {"lambda$charTyped$6", "lambda$charTyped$7"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/events/GuiEventListener;charTyped(CI)Z"))
	private static boolean fabricatedForgeTest$typed(GuiEventListener listener, char typed, int modifiers, Operation<Boolean> original) {
		CharProbeScreen.wrapped(listener);
		return original.call(listener, typed, modifiers);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void fabricatedForgeTest$send(CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (CharProbeScreen.sent() || client.getOverlay() != null) return;
		long window = client.getWindow().getWindow();
		CharProbeScreen probe = CharProbeScreen.show(client);
		try {
			charTyped(window, ONE_CHAR, 0);
			charTyped(window, TWO_CHARS, 0);
		} finally {
			probe.hide(client);
		}
		probe.report(ForgeTestMod.LOGGER);
	}
}
