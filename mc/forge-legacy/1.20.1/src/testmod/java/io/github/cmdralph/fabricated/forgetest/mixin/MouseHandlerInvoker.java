package io.github.cmdralph.fabricated.forgetest.mixin;

import net.minecraft.class_312;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** {@code MouseHandler.onPress}, which is private, for the screen check's clicks ({@code ForgeScreenClient}). */
@Mixin(class_312.class)
public interface MouseHandlerInvoker {
	@Invoker("method_1601")
	void fabricatedtest$onPress(long window, int button, int action, int modifiers);
}
