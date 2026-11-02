package io.github.cmdralph.fabricated.forge.screen.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.screen.ScreenInputEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraftforge.client.ForgeHooksClient;

/**
 * Fabric's key and drag events around the screen calls Forge's hooks make for its input handlers (see
 * {@link ScreenInputEvents}): each hook posts Forge's {@code Pre} event, calls the screen unless a listener took the
 * input, then posts Forge's {@code Post} event unless the screen took it.
 */
@Mixin(ForgeHooksClient.class)
abstract class ForgeHooksClientScreenInputMixin {
	@WrapOperation(method = "onScreenKeyPressed(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/input/KeyEvent;)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z"))
	private static boolean fabricated$keyPressed(Screen screen, KeyEvent event, Operation<Boolean> keyPressed) {
		return ScreenInputEvents.keyPressed(screen, event, keyPressed);
	}

	@WrapOperation(method = "onScreenKeyReleased(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/input/KeyEvent;)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;keyReleased(Lnet/minecraft/client/input/KeyEvent;)Z"))
	private static boolean fabricated$keyReleased(Screen screen, KeyEvent event, Operation<Boolean> keyReleased) {
		return ScreenInputEvents.keyReleased(screen, event, keyReleased);
	}

	@WrapOperation(method = "onScreenMouseDrag(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/input/MouseButtonEvent;DD)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z"))
	private static boolean fabricated$mouseDragged(Screen screen, MouseButtonEvent event, double dragX, double dragY, Operation<Boolean> mouseDragged) {
		return ScreenInputEvents.mouseDragged(screen, event, dragX, dragY, mouseDragged);
	}
}
