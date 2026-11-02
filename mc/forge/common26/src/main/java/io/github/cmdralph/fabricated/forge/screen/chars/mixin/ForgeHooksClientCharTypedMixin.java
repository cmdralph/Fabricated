package io.github.cmdralph.fabricated.forge.screen.chars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.screen.ScreenCharEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraftforge.client.ForgeHooksClient;

/** Fabric's character typing events around the screen call Forge's hook makes for the keyboard handler (see {@link ScreenCharEvents}). */
@Mixin(ForgeHooksClient.class)
abstract class ForgeHooksClientCharTypedMixin {
	@WrapOperation(method = "onScreenCharTyped(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/input/CharacterEvent;)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;charTyped(Lnet/minecraft/client/input/CharacterEvent;)Z"))
	private static boolean fabricated$charTyped(Screen screen, CharacterEvent event, Operation<Boolean> charTyped) {
		return ScreenCharEvents.charTyped(screen, event, charTyped);
	}
}
