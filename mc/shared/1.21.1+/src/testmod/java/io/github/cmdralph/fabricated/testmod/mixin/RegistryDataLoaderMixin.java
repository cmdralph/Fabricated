package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.testmod.TestMod;
import net.minecraft.resources.RegistryDataLoader;

/**
 * Writes to built-in registries where Wover does (REG-3): from this class's initializer, which NeoForge's data pack
 * registry event loads after its new-registry event froze the root and before its registration events open the rest.
 */
@Mixin(RegistryDataLoader.class)
public abstract class RegistryDataLoaderMixin {
	@Inject(method = "<clinit>", at = @At("HEAD"))
	private static void fabricatedTest$writeEarly(CallbackInfo ci) {
		TestMod.earlyRegistryWrites();
	}
}
