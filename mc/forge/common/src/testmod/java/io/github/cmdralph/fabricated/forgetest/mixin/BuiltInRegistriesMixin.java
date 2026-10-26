package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forgetest.ForgeBootstrapContent;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Builds content as vanilla's bootstrap freezes the registries, before any mod is constructed (CORPUS-31), as BetterNether's
 * entity types are built when its mixin into {@code DefaultAttributes} first loads their class.
 */
@Mixin(BuiltInRegistries.class)
public abstract class BuiltInRegistriesMixin {
	@Inject(method = "freeze", at = @At("HEAD"))
	private static void fabricatedForgeTest$buildContent(CallbackInfo ci) {
		ForgeBootstrapContent.build();
	}
}
