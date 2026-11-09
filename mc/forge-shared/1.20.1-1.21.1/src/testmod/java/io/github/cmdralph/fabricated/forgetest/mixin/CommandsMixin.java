package io.github.cmdralph.fabricated.forgetest.mixin;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forgetest.ForgeTestMod;

/**
 * Proves an {@code @Inject} into a constructor anywhere but a return applies on Forge 47, whose Mixin takes one only at
 * a return, through Fabricated's injection point (CTOR-4, CORPUS-103). As Fabric API's command hook does, one runs as
 * {@code Commands}' constructor sets its dispatcher's result consumer, once the game's commands are
 * registered, and gets the constructor's parameters. As Dripstone Fluid Lib's hook into {@code Minecraft}'s constructor
 * does, the other runs just after the constructor makes and stores its dispatcher, shifted by 2 past that construction,
 * and sees the stored dispatcher.
 */
@Mixin(Commands.class)
public abstract class CommandsMixin {
	@Unique
	private static boolean fabricatedForgeTest$logged;
	@Unique
	private static boolean fabricatedForgeTest$constructionLogged;

	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/CommandDispatcher;setConsumer(Lcom/mojang/brigadier/ResultConsumer;)V", remap = false))
	private void fabricatedForgeTest$beforeSetConsumer(Commands.CommandSelection environment, CommandBuildContext registryAccess, CallbackInfo ci) {
		if (fabricatedForgeTest$logged || environment == null || registryAccess == null) return;
		fabricatedForgeTest$logged = true;
		ForgeTestMod.LOGGER.info("FABRICATED-TEST constructor hook OK");
	}

	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/CommandDispatcher;<init>()V", shift = At.Shift.BY, by = 2, remap = false))
	private void fabricatedForgeTest$afterDispatcher(Commands.CommandSelection environment, CommandBuildContext registryAccess, CallbackInfo ci) {
		if (fabricatedForgeTest$constructionLogged || ((Commands) (Object) this).getDispatcher() == null) return;
		fabricatedForgeTest$constructionLogged = true;
		ForgeTestMod.LOGGER.info("FABRICATED-TEST constructor construction hook OK");
	}
}
