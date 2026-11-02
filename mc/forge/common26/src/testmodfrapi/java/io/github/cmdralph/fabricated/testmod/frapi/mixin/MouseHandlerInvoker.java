package io.github.cmdralph.fabricated.testmod.frapi.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;

/**
 * The mouse handler's entry point for a button, as the window's callback calls it, for the screen input check's
 * synthetic input ({@code ScreenInputCheck}). Private on Forge 64 and 65, public on 66, as Fabric's client game tests
 * call it.
 */
@Mixin(MouseHandler.class)
public interface MouseHandlerInvoker {
	@Invoker
	void invokeOnButton(long window, MouseButtonInfo button, int action);
}
