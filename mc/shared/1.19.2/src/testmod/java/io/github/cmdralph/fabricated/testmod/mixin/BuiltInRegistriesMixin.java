package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.testmod.BootstrapContent;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Builds content as vanilla's bootstrap has created the registries' contents, before any mod is constructed (CORPUS-31),
 * as BetterNether's entity types are built when its mixin into {@code DefaultAttributes} first loads their class; and
 * sees when the built-in registries are frozen, as Wover's mixin into their freeze does (CORPUS-32). Minecraft 1.19.2
 * creates the contents in the class initializer of the registries' holder, and freezes them in {@code freezeBuiltins}.
 */
@Mixin(BuiltInRegistries.class)
public abstract class BuiltInRegistriesMixin {
	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void fabricatedTest$buildContent(CallbackInfo ci) {
		BootstrapContent.build();
	}

	@Inject(method = "freezeBuiltins", at = @At("HEAD"))
	private static void fabricatedTest$frozen(CallbackInfo ci) {
		BootstrapContent.frozen();
	}
}
