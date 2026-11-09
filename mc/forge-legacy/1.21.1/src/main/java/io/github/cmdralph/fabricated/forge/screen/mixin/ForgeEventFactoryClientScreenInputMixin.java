package io.github.cmdralph.fabricated.forge.screen.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.screen.ScreenInputEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ForgeEventFactoryClient;

/**
 * Fabric's mouse click and release events around the screen calls Forge 52's event factory makes for the mouse handler
 * (see {@link ScreenInputEvents}): it posts Forge's {@code Pre} event, calls the screen unless a listener took the
 * click, then posts Forge's {@code Post} event with what the screen returned.
 */
@Mixin(ForgeEventFactoryClient.class)
abstract class ForgeEventFactoryClientScreenInputMixin {
	@WrapOperation(method = "onScreenMouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(DDI)Z"))
	private static boolean fabricated$mouseClicked(Screen screen, double mouseX, double mouseY, int button, Operation<Boolean> mouseClicked) {
		return ScreenInputEvents.mouseClicked(screen, mouseX, mouseY, button, mouseClicked);
	}

	@WrapOperation(method = "onScreenMouseReleased", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseReleased(DDI)Z"))
	private static boolean fabricated$mouseReleased(Screen screen, double mouseX, double mouseY, int button, Operation<Boolean> mouseReleased) {
		return ScreenInputEvents.mouseReleased(screen, mouseX, mouseY, button, mouseReleased);
	}
}
