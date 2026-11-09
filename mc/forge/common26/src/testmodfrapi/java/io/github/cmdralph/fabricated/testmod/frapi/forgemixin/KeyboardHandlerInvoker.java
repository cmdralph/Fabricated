package io.github.cmdralph.fabricated.testmod.frapi.forgemixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * The keyboard handler's entry points for a key and a typed character, as the window's callbacks call them, for the
 * screen input check's synthetic input ({@code ScreenInputCheck}). Private on Forge 64 and 65, public on 66, as Fabric's
 * client game tests call them.
 */
@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerInvoker {
	@Invoker
	void invokeKeyPress(long window, int action, KeyEvent event);

	@Invoker
	void invokeCharTyped(long window, CharacterEvent event);
}
