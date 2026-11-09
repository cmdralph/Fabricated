package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.cmdralph.fabricated.forge.BuiltInRegistriesFreeze;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Vanilla's bootstrap freezes the built-in registries without running {@code freeze} while Fabric mods' main entrypoints
 * are still to run: it runs after them, as on Fabric ({@link BuiltInRegistriesFreeze}, CORPUS-32).
 */
@Mixin(BuiltInRegistries.class)
abstract class BuiltInRegistriesFreezeMixin {
	@WrapOperation(method = "bootStrap", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/registries/BuiltInRegistries;freeze()V"))
	private static void fabricated$freezeAfterMainEntrypoints(Operation<Void> original) {
		if (!BuiltInRegistriesFreeze.deferBootstrapFreeze()) original.call();
	}
}
