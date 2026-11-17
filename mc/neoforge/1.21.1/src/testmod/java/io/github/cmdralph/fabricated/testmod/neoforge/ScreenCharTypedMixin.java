package io.github.cmdralph.fabricated.testmod.neoforge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.testmod.TestMod;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;

/**
 * A wrapper of the {@code GuiEventListener.charTyped} call vanilla's {@code KeyboardHandler.charTyped} lambdas make,
 * taking the listener and passing it on, as owo-lib's braid layers do; NeoForge 21.1's lambdas take the screen and call
 * the screen's {@code charTyped} (LAMBDA-14). Once a screen is shown with no overlay, the keyboard handler is sent a
 * character of each of its lambdas (one char, and one taking two, a surrogate pair), as GLFW's callback sends them; the
 * wrapper must get each with the shown screen as the listener, and its original call must reach the screen. The
 * characters are ones no screen acts on.
 */
@Mixin(KeyboardHandler.class)
public abstract class ScreenCharTypedMixin {
	/** A character of one char, and one of two (U+1F600), which go through the two lambdas. */
	@Unique
	private static final int ONE_CHAR = 'x';
	@Unique
	private static final int TWO_CHARS = 0x1F600;
	/** The wrapper's calls with the shown screen as the listener: the one char, then each of the pair's two. */
	@Unique
	private static int fabricatedTest$wrapped;
	@Unique
	private static boolean fabricatedTest$sent;

	@Shadow
	private void charTyped(long window, int codepoint, int modifiers) {
	}

	@WrapOperation(method = {"lambda$charTyped$6", "lambda$charTyped$7"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/events/GuiEventListener;charTyped(CI)Z"))
	private static boolean fabricatedTest$typed(GuiEventListener listener, char typed, int modifiers, Operation<Boolean> original) {
		boolean ours = typed == ONE_CHAR || Character.isSurrogate(typed);
		if (ours && fabricatedTest$sent && listener == Minecraft.getInstance().screen) fabricatedTest$wrapped++;
		return original.call(listener, typed, modifiers);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void fabricatedTest$send(CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (fabricatedTest$sent || client.screen == null || client.getOverlay() != null) return;
		fabricatedTest$sent = true;
		long window = client.getWindow().getWindow();
		charTyped(window, ONE_CHAR, 0);
		charTyped(window, TWO_CHARS, 0);
		if (fabricatedTest$wrapped == 3) {
			TestMod.LOGGER.info("FABRICATED-TEST screen char typed wrapper OK");
		} else {
			TestMod.LOGGER.error("FABRICATED-TEST screen char typed wrapper FAILED: the wrapper saw {} of the 3 characters sent to the shown screen",
					fabricatedTest$wrapped);
		}
	}
}
