package io.github.cmdralph.fabricated.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.registry.RegistrySyncBridge;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * A registry vanilla's own code adds to the frozen root registry before NeoForge's registration has ended gets the root
 * opened for it, as {@code FabricRegistryBuilder}'s registries do ({@link RegistrySyncBridge#openRootForLateRegistry}).
 */
@Mixin(MappedRegistry.class)
abstract class MappedRegistryRootMixin {
	@Shadow
	private boolean frozen;

	@Inject(method = "validateWrite(Lnet/minecraft/resources/ResourceKey;)V", at = @At("HEAD"))
	private void fabricated$openRootForLateRegistry(CallbackInfo ci) {
		if (frozen && (Object) this == BuiltInRegistries.REGISTRY) RegistrySyncBridge.openRootForLateRegistry();
	}
}
