package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forgetest.ForgeBootstrapContent;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Builds content as vanilla's bootstrap creates the registries' contents, before any mod is constructed (CORPUS-31), as
 * BetterNether's entity types are built when its mixin into {@code DefaultAttributes} first loads their class; and sees
 * when the built-in registries are frozen, as Wover's mixin into {@code freeze} does (CORPUS-32).
 */
@Mixin(BuiltInRegistries.class)
public abstract class BuiltInRegistriesMixin {
	@Inject(method = "createContents", at = @At("TAIL"))
	private static void fabricatedForgeTest$buildContent(CallbackInfo ci) {
		ForgeBootstrapContent.build();
	}

	@Inject(method = "freeze", at = @At("HEAD"))
	private static void fabricatedForgeTest$frozen(CallbackInfo ci) {
		ForgeBootstrapContent.frozen();
	}
}
