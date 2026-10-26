package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.forge.PendingHolders;

/**
 * Forge's wrapper of the vanilla registries it manages (blocks, items and entity types among them) replaces {@code
 * MappedRegistry}'s {@code freeze} with its own, which checks the same holders without calling vanilla's (read in Forge
 * 61 to 66's sources, as in Forge 47's, where the stack of the CORPUS-31 reproduction ends in it). This keeps them out
 * of its check as {@link MappedRegistryPendingHoldersMixin} does for vanilla's.
 */
@Mixin(targets = "net.minecraftforge.registries.NamespacedWrapper")
abstract class NamespacedWrapperPendingHoldersMixin {
	@Inject(method = "freeze", at = @At("HEAD"))
	private void fabricated$beforeFreeze(CallbackInfoReturnable<?> cir) {
		((PendingHolders) (Object) this).fabricated$keepPendingHolders();
	}

	@Inject(method = "freeze", at = @At("RETURN"))
	private void fabricated$afterFreeze(CallbackInfoReturnable<?> cir) {
		((PendingHolders) (Object) this).fabricated$restorePendingHolders();
	}
}
