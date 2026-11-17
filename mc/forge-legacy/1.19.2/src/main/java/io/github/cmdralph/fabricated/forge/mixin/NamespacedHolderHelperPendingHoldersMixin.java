package io.github.cmdralph.fabricated.forge.mixin;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.cmdralph.fabricated.forge.MainEntrypoints;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;

/**
 * Forge 43's registries that wrap vanilla's (blocks, items and entity types among them) keep their holders in a helper
 * of their own, whose {@code freeze} checks the intrusive ones as vanilla's does (read in Forge 43's sources). This keeps
 * the holders of content built before a Fabric mod's main entrypoint out of its check while the entrypoints are still to
 * run, as {@link MappedRegistryPendingHoldersMixin} does for vanilla's registries, and drops those a failed entrypoint
 * left once they have run.
 *
 * <p>The helper is Forge's, a class of its package: named as a string, and nothing in this mixin is remapped.
 */
@Mixin(targets = "net.minecraftforge.registries.NamespacedHolderHelper", remap = false)
abstract class NamespacedHolderHelperPendingHoldersMixin<T> {
	@Shadow
	@Final
	private Registry<T> self;
	@Shadow
	private Map<T, Holder.Reference<T>> holders;
	@Unique
	private Map<T, Holder.Reference<T>> fabricated$pendingHolders;

	@Inject(method = "freeze", at = @At("HEAD"))
	private void fabricated$beforeFreeze(CallbackInfoReturnable<?> cir) {
		List<Holder.Reference<T>> unbound = holders.values().stream()
				.filter(holder -> holder.getType() == Holder.Reference.Type.INTRUSIVE && !holder.isBound()).toList();
		if (unbound.isEmpty()) return;
		if (MainEntrypoints.pending()) {
			Map<T, Holder.Reference<T>> pending = new IdentityHashMap<>();
			holders.entrySet().removeIf(entry -> unbound.contains(entry.getValue()) && pending.put(entry.getKey(), entry.getValue()) == null);
			fabricated$pendingHolders = pending;
		} else if (MainEntrypoints.dropLeftoverHolders(self.key(), unbound)) {
			holders.values().removeAll(unbound);
		}
	}

	@Inject(method = "freeze", at = @At("RETURN"))
	private void fabricated$afterFreeze(CallbackInfoReturnable<?> cir) {
		if (fabricated$pendingHolders == null) return;
		holders.putAll(fabricated$pendingHolders);
		fabricated$pendingHolders = null;
	}
}
