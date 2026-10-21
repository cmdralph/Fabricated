package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.registry.RegistrySyncBridge;
import net.minecraft.core.MappedRegistry;

/**
 * A frozen built-in registry vanilla's own code writes to before NeoForge's registration has ended (a new registry in
 * the root, an entry in a vanilla registry) is opened for it, as the root is for {@code FabricRegistryBuilder}'s
 * registries ({@link RegistrySyncBridge#openForEarlyWrite}).
 */
@Mixin(MappedRegistry.class)
abstract class MappedRegistryEarlyWriteMixin {
	@Shadow
	private boolean frozen;

	@Inject(method = "validateWrite(Lnet/minecraft/resources/ResourceKey;)V", at = @At("HEAD"))
	private void fabricated$openForEarlyWrite(CallbackInfo ci) {
		if (frozen) RegistrySyncBridge.openForEarlyWrite((MappedRegistry<?>) (Object) this);
	}
}
