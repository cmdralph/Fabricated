package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.forge.BuiltInRegistriesFreeze;
import net.minecraftforge.registries.GameData;

/**
 * Forge's freeze after Fabric mods' main entrypoints runs vanilla's {@code BuiltInRegistries.freeze} first, if the
 * bootstrap's waits for them ({@link BuiltInRegistriesFreeze}, CORPUS-32). Forge's own class, whose names are not
 * remapped.
 */
@Mixin(value = GameData.class, remap = false)
abstract class GameDataFreezeMixin {
	@Inject(method = "freezeData", at = @At("HEAD"))
	private static void fabricated$freezeBuiltInRegistries(CallbackInfo ci) {
		BuiltInRegistriesFreeze.beforeFreezeData();
	}
}
