package io.github.cmdralph.fabricated.forgetest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forgetest.ForgeBootstrapContent;
import net.minecraft.class_7923;

/**
 * Builds content as vanilla's bootstrap freezes the registries ({@code BuiltInRegistries.freeze}, {@code method_47491}),
 * before any mod is constructed (CORPUS-31), as BetterNether's entity types are built when its mixin into
 * {@code DefaultAttributes} first loads their class.
 */
@Mixin(class_7923.class)
public abstract class BuiltInRegistriesMixin {
	@Inject(method = "method_47491", at = @At("HEAD"))
	private static void fabricatedForgeTest$buildContent(CallbackInfo ci) {
		ForgeBootstrapContent.build();
	}
}
