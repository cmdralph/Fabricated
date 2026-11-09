package io.github.cmdralph.fabricated.forgetest.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** {@code MouseHandler.onPress}, which is private, for the screen check's clicks ({@code ForgeScreenClient}). */
@Mixin(MouseHandler.class)
public interface MouseHandlerInvoker {
	@Invoker("onPress")
	void fabricatedtest$onPress(long window, int button, int action, int modifiers);
}
