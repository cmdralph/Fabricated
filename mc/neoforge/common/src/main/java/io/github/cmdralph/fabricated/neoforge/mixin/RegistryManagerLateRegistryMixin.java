package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.cmdralph.fabricated.neoforge.EntrypointBridge;
import net.neoforged.neoforge.registries.RegistryManager;

/**
 * A registry a Fabric initializer creates through NeoForge's registry tracking, as Forgified Fabric API's
 * {@code FabricRegistryBuilder} does, is let through after NeoForge's {@code NewRegistryEvent} (PROVIDER-6). Sinytra
 * Connector runs Fabric initializers before that event; Fabricated runs them in NeoForge's registration events, or in
 * {@code Minecraft}'s constructor on a client ({@link EntrypointBridge}), where NeoForge would refuse the registry. The
 * tracking only checks that a registry created before the event joins the root in it; the builder adds it to the root
 * itself. NeoForge's own mods keep the check.
 */
@Mixin(RegistryManager.class)
abstract class RegistryManagerLateRegistryMixin {
	@Shadow
	private static Set<?> pendingModdedRegistries;

	@Inject(method = "trackModdedRegistry", at = @At("HEAD"), cancellable = true)
	private static void fabricated$allowFabricInitializers(CallbackInfo ci) {
		if (pendingModdedRegistries == null && EntrypointBridge.inFabricEntrypoint()) ci.cancel();
	}
}
