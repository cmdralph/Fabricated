package io.github.cmdralph.fabricated.forge.screen.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.screen.ScreenInputEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraftforge.client.event.ForgeEventFactoryClient;

/**
 * Fabric's mouse click and release events around the screen calls Forge's event factory makes for the mouse handler
 * (see {@link ScreenInputEvents}): it posts Forge's {@code Pre} event, calls the screen unless a listener took the
 * click, then posts Forge's {@code Post} event with what the screen returned.
 */
@Mixin(ForgeEventFactoryClient.class)
abstract class ForgeEventFactoryClientScreenInputMixin {
	@WrapOperation(method = "onScreenMouseClicked(Lnet/minecraft/client/gui/screens/Screen;DDLnet/minecraft/client/input/MouseButtonEvent;Z)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"))
	private static boolean fabricated$mouseClicked(Screen screen, MouseButtonEvent event, boolean doubleClick, Operation<Boolean> mouseClicked) {
		return ScreenInputEvents.mouseClicked(screen, event, doubleClick, mouseClicked);
	}

	@WrapOperation(method = "onScreenMouseReleased(Lnet/minecraft/client/gui/screens/Screen;DDLnet/minecraft/client/input/MouseButtonEvent;)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z"))
	private static boolean fabricated$mouseReleased(Screen screen, MouseButtonEvent event, Operation<Boolean> mouseReleased) {
		return ScreenInputEvents.mouseReleased(screen, event, mouseReleased);
	}
}
