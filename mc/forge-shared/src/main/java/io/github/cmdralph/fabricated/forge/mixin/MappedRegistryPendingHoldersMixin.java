package io.github.cmdralph.fabricated.forge.mixin;

import java.util.IdentityHashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.forge.MainEntrypoints;
import io.github.cmdralph.fabricated.forge.PendingHolders;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;

/**
 * A Fabric mod whose content is built before its main entrypoint runs (a class initialized early, as BetterNether's
 * entity types are) registers it only there, and Fabric API leaves the registries open until then. Forge freezes them
 * during vanilla's bootstrap, before any mod is constructed (CORPUS-31): the content's intrusive holders are not
 * registered yet, and the check fails the game. While the main entrypoints are still to run, a freeze leaves them out
 * of the check and keeps them for the registration (Forge keeps the map past a freeze, since it freezes more than
 * once); the freeze after the registration events checks them as vanilla does. Forge's own registries replace this
 * {@code freeze} with theirs, which {@link NamespacedWrapperPendingHoldersMixin} wraps the same way.
 */
@Mixin(MappedRegistry.class)
abstract class MappedRegistryPendingHoldersMixin<T> implements PendingHolders {
	@Shadow
	private Map<T, Holder.Reference<T>> unregisteredIntrusiveHolders;
	@Unique
	private Map<T, Holder.Reference<T>> fabricated$pendingHolders;

	@Override
	public void fabricated$keepPendingHolders() {
		if (unregisteredIntrusiveHolders == null || unregisteredIntrusiveHolders.isEmpty()
				|| !MainEntrypoints.pending()) return;
		fabricated$pendingHolders = new IdentityHashMap<>(unregisteredIntrusiveHolders);
		unregisteredIntrusiveHolders.clear();
	}

	@Override
	public void fabricated$restorePendingHolders() {
		if (fabricated$pendingHolders == null) return;
		if (unregisteredIntrusiveHolders != null) unregisteredIntrusiveHolders.putAll(fabricated$pendingHolders);
		fabricated$pendingHolders = null;
	}

	@Inject(method = "freeze", at = @At("HEAD"))
	private void fabricated$beforeFreeze(CallbackInfoReturnable<?> cir) {
		fabricated$keepPendingHolders();
	}

	@Inject(method = "freeze", at = @At("RETURN"))
	private void fabricated$afterFreeze(CallbackInfoReturnable<?> cir) {
		fabricated$restorePendingHolders();
	}
}
