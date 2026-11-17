package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.forge.ProviderRegistries;
import net.minecraft.core.WritableRegistry;

/**
 * Fabric's {@code FabricRegistryBuilder} adds the registry it builds to the root registry, whichever mod provides
 * fabric-registry-sync-v0 ({@link ProviderRegistries#joinRoot}, PROVIDER-20). Pseudo: Mixin skips it without a word
 * when there is no Fabric API; the builder is Fabric API's own class, whose names are not remapped.
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder", remap = false)
abstract class FabricRegistryBuilderRootMixin {
	@Inject(method = "buildAndRegister", at = @At("RETURN"))
	private void fabricated$joinRoot(CallbackInfoReturnable<WritableRegistry<?>> cir) {
		ProviderRegistries.joinRoot(cir.getReturnValue());
	}
}
