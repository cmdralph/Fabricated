package io.github.cmdralph.fabricated.forge.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import io.github.cmdralph.fabricated.forge.MainEntrypoints;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.registries.ForgeRegistry;

/**
 * A write to a vanilla registry Forge manages before Fabric mods' main entrypoints run (CORPUS-71). Forge locks those
 * registries once it has taken its snapshot of vanilla's entries ({@code GameData.vanillaSnapshot}) and opens them only
 * for its registration events, where Fabricated runs the main entrypoints. On Fabric a registry takes entries until the
 * freeze Fabric API moves after the entrypoints, and a Fabric mod's class loaded earlier registers from its static
 * initializer: GeckoLib's constants, loaded on a client as the client bootstrap builds the special model renderers,
 * register its data components. Until the main entrypoints have run, a write to a locked registry opens it for that one
 * write and leaves it as it was, as NeoForge's {@code MappedRegistryEarlyWriteMixin} does for NeoForge's frozen ones.
 * Forge's own snapshot after its registration events ({@code GameData.freezeData}) takes the entry with the rest.
 */
@Mixin(targets = "net.minecraftforge.registries.NamespacedWrapper")
abstract class NamespacedWrapperEarlyWriteMixin {
	@Shadow
	boolean locked;

	@Shadow
	@Final
	private ForgeRegistry<?> delegate;

	@WrapMethod(method = "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;")
	private Holder.Reference<?> fabricated$openForEarlyWrite(ResourceKey<?> key, Object value, RegistrationInfo info,
			Operation<Holder.Reference<?>> register) {
		if (!locked || !MainEntrypoints.pending()) return register.call(key, value, info);
		boolean frozen = delegate.isLocked();
		// Clears the wrapper's lock with the registry's own (ForgeRegistry.unfreeze), which its add checks.
		delegate.unfreeze();
		try {
			return register.call(key, value, info);
		} finally {
			if (frozen) delegate.freeze();
			locked = true;
		}
	}
}
