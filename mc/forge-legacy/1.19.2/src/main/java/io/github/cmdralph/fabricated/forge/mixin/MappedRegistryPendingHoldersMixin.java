package io.github.cmdralph.fabricated.forge.mixin;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.forge.MainEntrypoints;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;

/**
 * A Fabric mod whose content is built before its main entrypoint runs (a class initialized early, as BetterNether's
 * entity types are) registers it only there, and Fabric API leaves the registries open until then. Forge 43 freezes them
 * during vanilla's bootstrap, before any mod is constructed (CORPUS-31): the content's intrusive holders are not
 * registered yet, and the check fails the game. While the main entrypoints are still to run, a freeze leaves them out
 * of the check and puts them back once it is done; the freeze after the registration events checks them as vanilla
 * does, unless a Fabric mod's entrypoint failed (see {@link MainEntrypoints#dropLeftoverHolders}). Vanilla 1.19.2's
 * freeze drops its map of intrusive holders, which Forge's {@code unfreeze} makes anew for its registration events: the
 * holders kept are put back in a map of their own. Forge's own registries keep their holders elsewhere, which
 * {@link NamespacedHolderHelperPendingHoldersMixin} handles the same way.
 */
@Mixin(MappedRegistry.class)
abstract class MappedRegistryPendingHoldersMixin<T> {
	@Shadow
	private Map<T, Holder.Reference<T>> intrusiveHolderCache;
	@Unique
	private Map<T, Holder.Reference<T>> fabricated$pendingHolders;

	@Inject(method = "freeze", at = @At("HEAD"))
	private void fabricated$beforeFreeze(CallbackInfoReturnable<?> cir) {
		// Vanilla 1.19.2 keeps a registered entry's holder in the map too: only the unbound ones are checked.
		if (intrusiveHolderCache == null) return;
		List<Holder.Reference<T>> unbound = intrusiveHolderCache.values().stream().filter(holder -> !holder.isBound()).toList();
		if (unbound.isEmpty()) return;
		if (MainEntrypoints.pending()) {
			fabricated$pendingHolders = new IdentityHashMap<>(intrusiveHolderCache);
			intrusiveHolderCache.clear();
		} else if (MainEntrypoints.dropLeftoverHolders(((Registry<?>) (Object) this).key(), unbound)) {
			intrusiveHolderCache.values().removeAll(unbound);
		}
	}

	@Inject(method = "freeze", at = @At("RETURN"))
	private void fabricated$afterFreeze(CallbackInfoReturnable<?> cir) {
		if (fabricated$pendingHolders == null) return;
		if (intrusiveHolderCache == null) intrusiveHolderCache = new IdentityHashMap<>();
		intrusiveHolderCache.putAll(fabricated$pendingHolders);
		fabricated$pendingHolders = null;
	}
}
