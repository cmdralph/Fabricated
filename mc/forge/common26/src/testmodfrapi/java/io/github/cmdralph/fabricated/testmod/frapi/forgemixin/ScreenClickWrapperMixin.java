package io.github.cmdralph.fabricated.testmod.frapi.forgemixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.testmod.frapi.ScreenInputCheck;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Wrappers of the screen's {@code mouseClicked} and {@code mouseReleased} calls vanilla's {@code MouseHandler.onButton}
 * makes, as Architectury's are, for the screen input check ({@link ScreenInputCheck}): Forge 64 to 66 hand the screen a
 * button through {@code ForgeEventFactoryClient.onScreenMouseClicked} and {@code onScreenMouseReleased}, which take the
 * mouse's position too, where pre-flight points the wrappers through bridges putting it back (LAMBDA-14). Each records
 * the check's button and makes the call. Test code only.
 */
@Mixin(MouseHandler.class)
abstract class ScreenClickWrapperMixin {
	@WrapOperation(method = "onButton", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"))
	private boolean fabricatedTest$clicked(Screen screen, MouseButtonEvent event, boolean doubleClick, Operation<Boolean> original) {
		ScreenInputCheck.wrapped(event, true);
		return original.call(screen, event, doubleClick);
	}

	@WrapOperation(method = "onButton", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/Screen;mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z"))
	private boolean fabricatedTest$released(Screen screen, MouseButtonEvent event, Operation<Boolean> original) {
		ScreenInputCheck.wrapped(event, false);
		return original.call(screen, event);
	}
}
