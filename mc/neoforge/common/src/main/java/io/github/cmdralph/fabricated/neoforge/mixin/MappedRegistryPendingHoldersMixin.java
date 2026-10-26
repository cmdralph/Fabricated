package io.github.cmdralph.fabricated.neoforge.mixin;

import java.util.IdentityHashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.neoforge.EntrypointBridge;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;

/**
 * A Fabric mod whose content is built before its main entrypoint runs (a class initialized early, as BetterNether's
 * entity types are) registers it only there, and Fabric API leaves the registries open until then. NeoForge freezes them
 * earlier: during vanilla's bootstrap, before any mod is constructed (CORPUS-31), and on a client before
 * {@code Minecraft} exists, whose constructor runs the main entrypoints (CORPUS-16). At those freezes the content's
 * intrusive holders are not registered yet, and vanilla's check fails the game. While the main entrypoints are still to
 * run, a freeze leaves them out of the check and keeps them for the registration; the freeze after the entrypoints
 * checks them as vanilla does.
 */
@Mixin(MappedRegistry.class)
abstract class MappedRegistryPendingHoldersMixin<T> {
	@Shadow
	private Map<T, Holder.Reference<T>> unregisteredIntrusiveHolders;
	@Unique
	private Map<T, Holder.Reference<T>> fabricated$pendingHolders;

	@Inject(method = "freeze", at = @At("HEAD"))
	private void fabricated$keepPendingHolders(CallbackInfoReturnable<?> cir) {
		if (unregisteredIntrusiveHolders == null || unregisteredIntrusiveHolders.isEmpty()
				|| !EntrypointBridge.mainEntrypointsPending()) return;
		fabricated$pendingHolders = new IdentityHashMap<>(unregisteredIntrusiveHolders);
		unregisteredIntrusiveHolders.clear();
	}

	@Inject(method = "freeze", at = @At("RETURN"))
	private void fabricated$restorePendingHolders(CallbackInfoReturnable<?> cir) {
		if (fabricated$pendingHolders == null) return;
		// NeoForge keeps the map past a freeze, since it freezes the registries more than once.
		if (unregisteredIntrusiveHolders != null) unregisteredIntrusiveHolders.putAll(fabricated$pendingHolders);
		fabricated$pendingHolders = null;
	}
}
