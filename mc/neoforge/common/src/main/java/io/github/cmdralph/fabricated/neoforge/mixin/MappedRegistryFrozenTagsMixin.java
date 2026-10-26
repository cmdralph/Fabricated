package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.registry.BuiltInRegistriesFreeze;
import net.minecraft.core.MappedRegistry;

/**
 * NeoForge's freeze binds every built-in registry's unbound tags to empty sets, which a frozen registry refuses. Run
 * after vanilla's {@code BuiltInRegistries.freeze}, which bound them as it froze each ({@link BuiltInRegistriesFreeze},
 * CORPUS-32), it leaves them as they are.
 */
@Mixin(MappedRegistry.class)
abstract class MappedRegistryFrozenTagsMixin {
	@Shadow
	private boolean frozen;

	@Inject(method = "bindAllTagsToEmpty", at = @At("HEAD"), cancellable = true)
	private void fabricated$keepBoundTags(CallbackInfo ci) {
		if (BuiltInRegistriesFreeze.tagsBound(frozen)) ci.cancel();
	}
}
