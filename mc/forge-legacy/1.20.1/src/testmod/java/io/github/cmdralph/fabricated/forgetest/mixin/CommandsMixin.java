package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forgetest.ForgeTestMod;
import net.minecraft.class_2170;
import net.minecraft.class_7157;

/**
 * Proves an {@code @Inject} at a call in a constructor applies on Forge 47, whose Mixin takes one only at a return
 * (CTOR-4): as Fabric API's command hook does, it runs as {@code Commands}' constructor ({@code class_2170}) sets its
 * dispatcher's result consumer, once the game's commands are registered, and gets the constructor's parameters.
 */
@Mixin(class_2170.class)
public abstract class CommandsMixin {
	@Unique
	private static boolean fabricatedForgeTest$logged;

	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/CommandDispatcher;setConsumer(Lcom/mojang/brigadier/ResultConsumer;)V", remap = false))
	private void fabricatedForgeTest$beforeSetConsumer(class_2170.class_5364 environment, class_7157 registryAccess, CallbackInfo ci) {
		if (fabricatedForgeTest$logged || environment == null || registryAccess == null) return;
		fabricatedForgeTest$logged = true;
		ForgeTestMod.LOGGER.info("FABRICATED-FORGE-TEST constructor hook OK");
	}
}
