package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.EntrypointBridge;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

/**
 * Leaves the check that every living entity type has attributes, which only logs, to the registries' freeze after Fabric
 * mods' main entrypoints (ATTR-1): on a client, NeoForge's first freeze comes before them (CORPUS-16), and logged an
 * error for each entity type whose mod registers its attributes there, as Fabric mods do. The freeze after them checks.
 */
@Mixin(DefaultAttributes.class)
abstract class DefaultAttributesValidationMixin {
	@Inject(method = "validate", at = @At("HEAD"), cancellable = true)
	private static void fabricated$validateAfterMainEntrypoints(CallbackInfo ci) {
		if (EntrypointBridge.mainEntrypointsPending()) ci.cancel();
	}
}
