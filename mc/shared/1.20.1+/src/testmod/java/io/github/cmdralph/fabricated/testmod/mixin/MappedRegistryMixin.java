package io.github.cmdralph.fabricated.testmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.testmod.BootstrapContent;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;

/**
 * Sees each registry frozen, as Flashback's mixin into {@code MappedRegistry.freeze} does (REG-8): on Fabric no freeze
 * comes before the main initializers, so a Fabric mod's handler there never runs before its mod started.
 */
@Mixin(MappedRegistry.class)
public abstract class MappedRegistryMixin {
	@Inject(method = "freeze", at = @At("HEAD"))
	private void fabricatedTest$freezing(CallbackInfoReturnable<Registry<?>> cir) {
		BootstrapContent.registryFreezing();
	}
}
